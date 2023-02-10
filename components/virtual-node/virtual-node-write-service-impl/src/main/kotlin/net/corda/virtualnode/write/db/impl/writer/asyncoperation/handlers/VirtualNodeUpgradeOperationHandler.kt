package net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers

import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import net.corda.data.virtualnode.VirtualNodeUpgradeRequest
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogRepository
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogRepositoryImpl
import net.corda.libs.virtualnode.common.exception.CpiNotFoundException
import net.corda.libs.virtualnode.datamodel.VirtualNodeNotFoundException
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepositoryImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.orm.utils.transaction
import net.corda.schema.Schemas
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.write.db.impl.writer.CpiMetadataLite
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeEntityRepository
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.MigrationUtility
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.VirtualNodeAsyncOperationHandler
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.exception.MgmGroupMismatchException
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.exception.VirtualNodeStateException
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

        upgradeVirtualNodeCpi(requestTimestamp, requestId, request)

        return null
    }

    private fun upgradeVirtualNodeCpi(
        requestTimestamp: Instant,
        requestId: String,
        request: VirtualNodeUpgradeRequest
    ) {
        val (upgradedVNodeInfo, cpkChangelogs, vaultDdlConnectionId, vaultDmlConnectionId) =
            entityManagerFactory.createEntityManager().transaction { em ->
                validateAndUpgradeVirtualNodeEntity(em, request, requestId, requestTimestamp)
            }

        publishVirtualNodeInfo(upgradedVNodeInfo)

        if (migrationUtility.isVaultSchemaAndTargetCpiInSync(cpkChangelogs, vaultDmlConnectionId)) {
            logger.info(
                "Virtual node upgrade complete, vault schema in sync with CPI, no migrations were necessary - Virtual node " +
                        "${upgradedVNodeInfo.holdingIdentity.shortHash} successfully upgraded to CPI " +
                        "name: ${upgradedVNodeInfo.cpiIdentifier.name}, version: ${upgradedVNodeInfo.cpiIdentifier.version} " +
                        "(request $requestId)"
            )
            publishVirtualNodeInfo(completeVirtualNodeOperation(request.virtualNodeShortHash))
            return
        }

        if (vaultDdlConnectionId == null) {
            logger.info("No vault DDL connection provided, CPI migrations must be run out of process (request $requestId)")
            return
        }

        tryRunningMigrationsInProcess(cpkChangelogs, vaultDdlConnectionId, requestId, request)

        logger.info(
            "Virtual node upgrade with CPI migrations complete - Virtual node " +
                    "${upgradedVNodeInfo.holdingIdentity.shortHash} successfully upgraded to CPI " +
                    "name: ${upgradedVNodeInfo.cpiIdentifier.name}, version: ${upgradedVNodeInfo.cpiIdentifier.version} " +
                    "(request $requestId)"
        )
        publishVirtualNodeInfo(completeVirtualNodeOperation(request.virtualNodeShortHash))
    }

    private fun validateAndUpgradeVirtualNodeEntity(
        em: EntityManager,
        request: VirtualNodeUpgradeRequest,
        requestId: String,
        requestTimestamp: Instant
    ): UpgradeTransactionCompleted {
        val currentVirtualNode = findCurrentVirtualNode(em, request.virtualNodeShortHash)

        if (currentVirtualNode.vaultDbOperationalStatus != OperationalStatus.INACTIVE) {
            // a future iteration of this will check first to see if migrations are actually required
            throw VirtualNodeStateException("Virtual node must be in maintenance before upgrade (request $requestId).")
        }

        val targetCpiMetadata = findTargetCpi(request.cpiFileChecksum)
        val originalCpiMetadata = findCurrentCpi(
            em,
            currentVirtualNode.cpiIdentifier.name,
            currentVirtualNode.cpiIdentifier.version,
            currentVirtualNode.cpiIdentifier.signerSummaryHash.toString()
        )

        validateCpiInSameGroup(originalCpiMetadata, targetCpiMetadata)

        val upgradedVnodeInfo = virtualNodeRepository.upgradeVirtualNodeCpi(
            em,
            request.virtualNodeShortHash,
            targetCpiMetadata.id.name, targetCpiMetadata.id.version, targetCpiMetadata.id.signerSummaryHash.toString(),
            requestId, requestTimestamp, request.toString()
        )

        val migrationChangelogs: List<CpkDbChangeLog> = cpkDbChangeLogRepository.findByCpiId(em, targetCpiMetadata.id)

        return UpgradeTransactionCompleted(
            upgradedVnodeInfo,
            migrationChangelogs,
            upgradedVnodeInfo.vaultDdlConnectionId,
            upgradedVnodeInfo.vaultDmlConnectionId
        )
    }

    private fun tryRunningMigrationsInProcess(
        changelogs: List<CpkDbChangeLog>,
        vaultDdlConnectionId: UUID,
        requestId: String,
        request: VirtualNodeUpgradeRequest
    ) {
        logger.info("Vault DDL connection found for virtual node, preparing to run CPI migrations (request $requestId)")
        migrationUtility.runVaultMigrations(
            ShortHash.of(request.virtualNodeShortHash),
            changelogs,
            vaultDdlConnectionId
        )

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
        val cpkChangelogs: List<CpkDbChangeLog>,
        val vaultDdlConnectionId: UUID?,
        val vaultDmlConnectionId: UUID
    )

    private fun findCurrentCpi(em: EntityManager, cpiName: String, cpiVersion: String, cpiSignerSummaryHash: String): CpiMetadataLite {
        return requireNotNull(
            oldVirtualNodeEntityRepository.getCPIMetadataByNameAndVersion(em, cpiName, cpiVersion, cpiSignerSummaryHash)
        ) {
            "CPI with name $cpiName, version $cpiVersion was not found."
        }
    }

    private fun validateCpiInSameGroup(
        currentCpiMetadata: CpiMetadataLite,
        upgradeCpiMetadata: CpiMetadataLite
    ) {
        if (currentCpiMetadata.mgmGroupId != upgradeCpiMetadata.mgmGroupId) {
            throw MgmGroupMismatchException(currentCpiMetadata.mgmGroupId, upgradeCpiMetadata.mgmGroupId)
        }
    }

    private fun findCurrentVirtualNode(em: EntityManager, holdingIdentityShortHash: String): VirtualNodeInfo {
        return virtualNodeRepository.find(em, ShortHash.Companion.of(holdingIdentityShortHash))
            ?: throw VirtualNodeNotFoundException(holdingIdentityShortHash)
    }

    private fun findTargetCpi(cpiFileChecksum: String): CpiMetadataLite {
        return oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(cpiFileChecksum)
            ?: throw CpiNotFoundException("CPI with file checksum $cpiFileChecksum was not found.")
    }

    private fun VirtualNodeUpgradeRequest.validateMandatoryFields() {
        requireNotNull(virtualNodeShortHash) {
            "Virtual node identifier is missing"
        }
        requireNotNull(cpiFileChecksum) {
            "CPI file checksum is missing"
        }
    }
}