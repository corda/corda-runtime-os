package net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers

import net.corda.crypto.core.ShortHash
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import net.corda.data.virtualnode.VirtualNodeUpgradeRequest
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogRepository
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogRepositoryImpl
import net.corda.libs.virtualnode.datamodel.dto.VirtualNodeOperationType
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepositoryImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.orm.utils.transaction
import net.corda.schema.Schemas
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.CpiMetadataLite
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeEntityRepository
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.MigrationUtility
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.VirtualNodeAsyncOperationHandler
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.exception.MigrationsFailedException
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.exception.VirtualNodeUpgradeRejectedException
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class VirtualNodeUpgradeOperationHandler(
    private val entityManagerFactory: EntityManagerFactory,
    private val oldVirtualNodeEntityRepository: VirtualNodeEntityRepository,
    private val virtualNodeInfoPublisher: Publisher,
    private val migrationUtility: MigrationUtility,
    private val cpkDbChangeLogRepository: CpkDbChangeLogRepository = CpkDbChangeLogRepositoryImpl(),
    private val virtualNodeRepository: VirtualNodeRepository = VirtualNodeRepositoryImpl()
) : VirtualNodeAsyncOperationHandler<VirtualNodeUpgradeRequest> {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun handle(
        requestTimestamp: Instant,
        requestId: String,
        request: VirtualNodeUpgradeRequest
    ): Record<*, *>? {
        logger.info("Virtual node upgrade operation requested by ${request.actor} at $requestTimestamp: $request ")
        request.validateMandatoryFields()

        try {
            upgradeVirtualNodeCpi(requestTimestamp, requestId, request)
        } catch (e: VirtualNodeUpgradeRejectedException) {
            logger.info("Virtual node upgrade (request $requestId) validation failed: ${e.message}")
            handleValidationFailed(request, requestId, requestTimestamp, e)
        } catch (e: MigrationsFailedException) {
            logger.warn("Virtual node upgrade (request $requestId) failed to run migrations: ${e.message}")
            handleMigrationsFailed(request, requestId, requestTimestamp, e)
        }

        return null
    }

    private fun handleMigrationsFailed(
        request: VirtualNodeUpgradeRequest,
        requestId: String,
        requestTimestamp: Instant,
        e: MigrationsFailedException
    ) {
        entityManagerFactory.createEntityManager().transaction { em ->
            virtualNodeRepository.failedMigrationsOperation(
                em,
                request.virtualNodeShortHash,
                requestId,
                request.toString(),
                requestTimestamp,
                e.reason,
                VirtualNodeOperationType.UPGRADE
            )
        }
    }

    private fun handleValidationFailed(
        request: VirtualNodeUpgradeRequest,
        requestId: String,
        requestTimestamp: Instant,
        e: VirtualNodeUpgradeRejectedException
    ) {
        entityManagerFactory.createEntityManager().transaction { em ->
            virtualNodeRepository.rejectedOperation(
                em,
                request.virtualNodeShortHash,
                requestId,
                request.toString(),
                requestTimestamp,
                e.reason,
                VirtualNodeOperationType.UPGRADE
            )
        }
    }

    private fun upgradeVirtualNodeCpi(
        requestTimestamp: Instant,
        requestId: String,
        request: VirtualNodeUpgradeRequest
    ) {
        val (upgradedVNodeInfo, cpkChangelogs) = entityManagerFactory.createEntityManager().transaction { em ->
            val targetCpi = validateUpgradeRequest(em, request, requestId)
            upgradeVirtualNodeEntity(em, request, requestId, requestTimestamp, targetCpi)
        }

        publishVirtualNodeInfo(upgradedVNodeInfo)

        if (migrationUtility.isVaultSchemaAndTargetCpiInSync(
                request.virtualNodeShortHash,
                cpkChangelogs,
                upgradedVNodeInfo.vaultDmlConnectionId
            )
        ) {
            logger.info(
                "Virtual node upgrade complete, vault schema in sync with CPI, no migrations were necessary - Virtual node " +
                        "${upgradedVNodeInfo.holdingIdentity.shortHash} successfully upgraded to CPI " +
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
    private fun validateUpgradeRequest(em: EntityManager, request: VirtualNodeUpgradeRequest, requestId: String): CpiMetadataLite {
        val currentVirtualNode = virtualNodeRepository.find(em, ShortHash.Companion.of(request.virtualNodeShortHash))
            ?: throw VirtualNodeUpgradeRejectedException("Holding identity ${request.virtualNodeShortHash} not found", requestId)

        if (currentVirtualNode.operationInProgress != null) {
            throw VirtualNodeUpgradeRejectedException("Operation ${currentVirtualNode.operationInProgress} already in progress", requestId)
        }

        if (currentVirtualNode.vaultDbOperationalStatus != OperationalStatus.INACTIVE) {
            throw VirtualNodeUpgradeRejectedException("Virtual node must be in maintenance", requestId)
        }

        val targetCpiMetadata = oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(request.cpiFileChecksum)
            ?: throw VirtualNodeUpgradeRejectedException("CPI with file checksum ${request.cpiFileChecksum} was not found", requestId)

        val originalCpiMetadata = oldVirtualNodeEntityRepository.getCPIMetadataByNameAndVersion(
            em,
            currentVirtualNode.cpiIdentifier.name,
            currentVirtualNode.cpiIdentifier.version,
            currentVirtualNode.cpiIdentifier.signerSummaryHash.toString()
        ) ?: throw VirtualNodeUpgradeRejectedException(
            "CPI with name ${currentVirtualNode.cpiIdentifier.name}, version ${currentVirtualNode.cpiIdentifier.version} was not found",
            requestId
        )

        if (originalCpiMetadata.mgmGroupId != targetCpiMetadata.mgmGroupId) {
            throw VirtualNodeUpgradeRejectedException(
                "Expected MGM GroupId ${originalCpiMetadata.mgmGroupId} but was ${targetCpiMetadata.mgmGroupId} in CPI", requestId
            )
        }

        return targetCpiMetadata
    }

    private fun upgradeVirtualNodeEntity(
        em: EntityManager,
        request: VirtualNodeUpgradeRequest,
        requestId: String,
        requestTimestamp: Instant,
        targetCpiMetadata: CpiMetadataLite
    ): UpgradeTransactionCompleted {
        val upgradedVnodeInfo = virtualNodeRepository.upgradeVirtualNodeCpi(
            em,
            request.virtualNodeShortHash,
            targetCpiMetadata.id.name, targetCpiMetadata.id.version, targetCpiMetadata.id.signerSummaryHash.toString(),
            requestId, requestTimestamp, request.toString()
        )

        val migrationChangelogs: List<CpkDbChangeLog> = cpkDbChangeLogRepository.findByCpiId(em, targetCpiMetadata.id)

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
            virtualNodeRepository.completeOperation(em, virtualNodeShortHash)
        }
    }

    private fun publishVirtualNodeInfo(virtualNodeInfo: VirtualNodeInfo) {
        virtualNodeInfoPublisher.publish(
            listOf(
                Record(Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC, virtualNodeInfo.holdingIdentity.toAvro(), virtualNodeInfo.toAvro())
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
