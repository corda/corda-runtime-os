package net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers

import net.corda.crypto.core.ShortHash
import net.corda.data.virtualnode.VirtualNodeUpgradeRequest
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogRepository
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogRepositoryImpl
import net.corda.libs.external.messaging.ExternalMessagingRouteConfigGenerator
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.virtualnode.common.exception.LiquibaseDiffCheckFailedException
import net.corda.libs.virtualnode.datamodel.dto.VirtualNodeOperationStateDto
import net.corda.libs.virtualnode.datamodel.dto.VirtualNodeOperationType
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepositoryImpl
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.orm.utils.transaction
import net.corda.schema.Schemas
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeEntityRepository
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.MigrationUtility
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.VirtualNodeAsyncOperationHandler
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.exception.MigrationsFailedException
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.exception.VirtualNodeUpgradeRejectedException
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

@Suppress("LongParameterList")
internal class VirtualNodeUpgradeOperationHandler(
    private val entityManagerFactory: EntityManagerFactory,
    private val oldVirtualNodeEntityRepository: VirtualNodeEntityRepository,
    private val virtualNodeInfoPublisher: Publisher,
    private val migrationUtility: MigrationUtility,
    private val cpkDbChangeLogRepository: CpkDbChangeLogRepository = CpkDbChangeLogRepositoryImpl(),
    private val virtualNodeRepository: VirtualNodeRepository = VirtualNodeRepositoryImpl(),
    private val externalMessagingRouteConfigGenerator: ExternalMessagingRouteConfigGenerator
) : VirtualNodeAsyncOperationHandler<VirtualNodeUpgradeRequest> {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun handle(
        requestTimestamp: Instant,
        requestId: String,
        request: VirtualNodeUpgradeRequest
    ) {
        logger.info("Virtual node upgrade operation requested by ${request.actor} at $requestTimestamp: $request ")
        request.validateMandatoryFields()

        try {
            upgradeVirtualNodeCpi(requestTimestamp, requestId, request)
        } catch (e: Exception) {
            handleUpgradeException(e, requestId, request, requestTimestamp)
        }
    }

    /**
     * If migrations have failed, we do not roll back the changes to the VirtualNode entity. We remove the operationInProgress (since there
     * is no longer an active operation). Some migrations could have run on the vault, while others may have failed. In this situation, the
     * virtual node operator is restricted from transitioning the virtual node to "ACTIVE" state because the vault won't be in sync with
     * the currently associated CPI. In this situation, it is up to DB admin to correct the vault using liquibase commands to manually run
     * migrations. Alternatively, if the migrations failed due to some internal server error, the virtual node operator can re-trigger the
     * upgrade and have corda re-attempt the migrations.
     */
    private fun writeFailedOperationEntity(
        request: VirtualNodeUpgradeRequest,
        requestId: String,
        requestTimestamp: Instant,
        state: VirtualNodeOperationStateDto,
        reason: String
    ): VirtualNodeInfo {
        return entityManagerFactory.createEntityManager().transaction { em ->
            virtualNodeRepository.failedOperation(
                em,
                request.virtualNodeShortHash,
                requestId,
                request.toString(),
                requestTimestamp,
                reason,
                VirtualNodeOperationType.UPGRADE,
                state
            )
        }
    }

    private fun upgradeVirtualNodeCpi(
        requestTimestamp: Instant,
        requestId: String,
        request: VirtualNodeUpgradeRequest
    ) {
        val (upgradedVNodeInfo, cpkChangelogs) = entityManagerFactory.createEntityManager().transaction { em ->
            val (virtualNode, targetCpi) = validateUpgradeRequest(em, request, requestId)

            val externalMessagingRouteConfig = externalMessagingRouteConfigGenerator.generateUpgradeConfig(
                virtualNode,
                targetCpi.cpiId,
                targetCpi.cpksMetadata
            )

            upgradeVirtualNodeEntity(em, request, requestId, requestTimestamp, targetCpi, externalMessagingRouteConfig)
        }

        publishVirtualNodeInfo(upgradedVNodeInfo)

        if (migrationUtility.areChangesetsDeployedOnVault(
                request.virtualNodeShortHash,
                cpkChangelogs,
                upgradedVNodeInfo.vaultDmlConnectionId
            )
        ) {
            logger.info(
                "Virtual node upgrade complete, no migrations necessary to upgrade virtual node ${request.virtualNodeShortHash} to CPI " +
                        "'${request.cpiFileChecksum}'. Virtual node successfully upgraded to CPI " +
                        "name: ${upgradedVNodeInfo.cpiIdentifier.name}, version: ${upgradedVNodeInfo.cpiIdentifier.version} " +
                        "(request $requestId)"
            )
            publishVirtualNodeInfo(completeVirtualNodeOperation(request.virtualNodeShortHash))
            return
        }

        if (upgradedVNodeInfo.vaultDdlConnectionId == null) {
            logger.info("No vault DDL connection provided, CPI migrations must be run out of process (request $requestId)")
            return
        }

        tryRunningMigrationsInProcess(cpkChangelogs, upgradedVNodeInfo.vaultDdlConnectionId!!, requestId, request)

        logger.info(
            "Virtual node upgrade with CPI migrations complete - Virtual node " +
                    "${upgradedVNodeInfo.holdingIdentity.shortHash} successfully upgraded to CPI " +
                    "name: ${upgradedVNodeInfo.cpiIdentifier.name}, version: ${upgradedVNodeInfo.cpiIdentifier.version} " +
                    "(request $requestId)"
        )
        publishVirtualNodeInfo(completeVirtualNodeOperation(request.virtualNodeShortHash))
    }

    @Suppress("ThrowsCount")
    private fun validateUpgradeRequest(
        em: EntityManager,
        request: VirtualNodeUpgradeRequest,
        requestId: String
    ): Pair<VirtualNodeInfo, CpiMetadata> {
        val currentVirtualNode = virtualNodeRepository.find(em, ShortHash.Companion.of(request.virtualNodeShortHash))
            ?: throw VirtualNodeUpgradeRejectedException(
                "Holding identity ${request.virtualNodeShortHash} not found",
                requestId
            )

        if (currentVirtualNode.operationInProgress != null) {
            throw VirtualNodeUpgradeRejectedException(
                "Operation ${currentVirtualNode.operationInProgress} already in progress",
                requestId
            )
        }

        if (currentVirtualNode.vaultDbOperationalStatus != OperationalStatus.INACTIVE) {
            throw VirtualNodeUpgradeRejectedException("Virtual node must be in maintenance", requestId)
        }

        val targetCpiMetadata = oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(request.cpiFileChecksum)
            ?: throw VirtualNodeUpgradeRejectedException(
                "CPI with file checksum ${request.cpiFileChecksum} was not found",
                requestId
            )

        val originalCpiMetadata = oldVirtualNodeEntityRepository.getCPIMetadataById(
            em,
            currentVirtualNode.cpiIdentifier
        ) ?: throw VirtualNodeUpgradeRejectedException(
            "CPI with name ${currentVirtualNode.cpiIdentifier.name}, version ${currentVirtualNode.cpiIdentifier.version} was not found",
            requestId
        )

        val originalMgmGroupId = GroupPolicyParser.groupIdFromJson(originalCpiMetadata.groupPolicy!!)
        val targetMgmGroupId = GroupPolicyParser.groupIdFromJson(targetCpiMetadata.groupPolicy!!)
        if (originalMgmGroupId != targetMgmGroupId) {
            throw VirtualNodeUpgradeRejectedException(
                "Expected MGM GroupId $originalMgmGroupId but was $targetMgmGroupId in CPI", requestId
            )
        }

        return Pair(currentVirtualNode, targetCpiMetadata)
    }

    private fun upgradeVirtualNodeEntity(
        em: EntityManager,
        request: VirtualNodeUpgradeRequest,
        requestId: String,
        requestTimestamp: Instant,
        targetCpiMetadata: CpiMetadata,
        externalMessagingRouteConfig: String?
    ): UpgradeTransactionCompleted {
        val upgradedVnodeInfo = virtualNodeRepository.upgradeVirtualNodeCpi(
            em,
            request.virtualNodeShortHash,
            targetCpiMetadata.cpiId.name,
            targetCpiMetadata.cpiId.version,
            targetCpiMetadata.cpiId.signerSummaryHash.toString(),
            externalMessagingRouteConfig,
            requestId,
            requestTimestamp,
            request.toString()
        )

        val migrationChangelogs: List<CpkDbChangeLog> =
            cpkDbChangeLogRepository.findByCpiId(em, targetCpiMetadata.cpiId)

        return UpgradeTransactionCompleted(
            upgradedVnodeInfo,
            migrationChangelogs
        )
    }

    private fun tryRunningMigrationsInProcess(
        changelogs: List<CpkDbChangeLog>,
        vaultDdlConnectionId: UUID,
        requestId: String,
        request: VirtualNodeUpgradeRequest
    ) {
        logger.info("Vault DDL connection found for virtual node, preparing to run CPI migrations (request $requestId)")
        try {
            migrationUtility.runVaultMigrations(
                ShortHash.of(request.virtualNodeShortHash),
                changelogs,
                vaultDdlConnectionId
            )
        } catch (e: VirtualNodeWriteServiceException) {
            val backupMsg = "Migrations failed for virtual node upgrade (request $requestId)"
            val msg = e.cause?.message ?: e.message
            throw MigrationsFailedException(msg ?: backupMsg, e)
        } catch (e: Exception) {
            val backupMsg = "Migrations failed for virtual node upgrade (request $requestId)"
            val msg = e.message ?: e.message
            throw MigrationsFailedException(msg ?: backupMsg, e)
        }

        // todo cs - as part of https://r3-cev.atlassian.net/browse/CORE-9046
//        if (!migrationUtility.isVaultSchemaAndTargetCpiInSync(changelogs, vaultDmlConnectionId)) {
//            log.info(
//                "After running CPI migrations, the vault schema is not in sync with the schema defined in the virtual node's CPI. " +
//                        "Manual intervention necessary (request $requestId)"
//            )
//            return
//        }
    }

    private fun completeVirtualNodeOperation(virtualNodeShortHash: String): VirtualNodeInfo {
        return entityManagerFactory.createEntityManager().transaction { em ->
            virtualNodeRepository.completedOperation(em, virtualNodeShortHash)
        }
    }

    private fun handleUpgradeException(
        e: Exception,
        requestId: String,
        request: VirtualNodeUpgradeRequest,
        requestTimestamp: Instant
    ) {
        when (e) {
            is VirtualNodeUpgradeRejectedException -> {
                logger.info("Virtual node upgrade (request $requestId) validation failed: ${e.message}")
                val vNodeInfo = writeFailedOperationEntity(
                    request, requestId, requestTimestamp, VirtualNodeOperationStateDto.VALIDATION_FAILED, e.reason
                )
                publishVirtualNodeInfo(vNodeInfo)
            }

            is MigrationsFailedException -> {
                logger.warn("Virtual node upgrade (request $requestId) failed to run migrations: ${e.message}")
                val vNodeInfo = writeFailedOperationEntity(
                    request, requestId, requestTimestamp, VirtualNodeOperationStateDto.MIGRATIONS_FAILED, e.reason
                )
                publishVirtualNodeInfo(vNodeInfo)
            }

            is LiquibaseDiffCheckFailedException -> {
                logger.warn("Unable to determine if vault for virtual node ${request.virtualNodeShortHash} is in sync with CPI.")
                val vNodeInfo = writeFailedOperationEntity(
                    request,
                    requestId,
                    requestTimestamp,
                    VirtualNodeOperationStateDto.LIQUIBASE_DIFF_CHECK_FAILED,
                    e.reason
                )
                publishVirtualNodeInfo(vNodeInfo)
            }

            else -> {
                logger.warn("Virtual node upgrade (request $requestId) could not complete due to exception: ${e.message}")
                val vNodeInfo = writeFailedOperationEntity(
                    request,
                    requestId,
                    requestTimestamp,
                    VirtualNodeOperationStateDto.UNEXPECTED_FAILURE,
                    e.message ?: "Unexpected failure"
                )
                publishVirtualNodeInfo(vNodeInfo)
            }
        }
    }

    private fun publishVirtualNodeInfo(virtualNodeInfo: VirtualNodeInfo) {
        virtualNodeInfoPublisher.publish(
            listOf(
                Record(
                    Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC,
                    virtualNodeInfo.holdingIdentity.toAvro(),
                    virtualNodeInfo.toAvro()
                )
            )
        )
    }

    data class UpgradeTransactionCompleted(
        val upgradedVirtualNodeInfo: VirtualNodeInfo,
        val cpkChangelogs: List<CpkDbChangeLog>
    )

    private fun VirtualNodeUpgradeRequest.validateMandatoryFields() {
        requireNotNull(virtualNodeShortHash) {
            "Virtual node identifier is missing"
        }
        requireNotNull(cpiFileChecksum) {
            "CPI file checksum is missing"
        }
    }
}