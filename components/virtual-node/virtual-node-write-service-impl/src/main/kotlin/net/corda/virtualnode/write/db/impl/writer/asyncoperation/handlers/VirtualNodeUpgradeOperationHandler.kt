package net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers

import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import net.corda.data.virtualnode.VirtualNodeUpgradeRequest
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.libs.cpi.datamodel.findCurrentCpkChangeLogsForCpi
import net.corda.libs.virtualnode.common.exception.CpiNotFoundException
import net.corda.libs.virtualnode.datamodel.VirtualNodeNotFoundException
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepositoryImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.orm.utils.transaction
import net.corda.schema.Schemas
import net.corda.v5.base.util.contextLogger
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

@Suppress("LongParameterList")
internal class VirtualNodeUpgradeOperationHandler(
    private val entityManagerFactory: EntityManagerFactory,
    private val oldVirtualNodeEntityRepository: VirtualNodeEntityRepository,
    private val virtualNodeInfoPublisher: Publisher,
    private val migrationUtility: MigrationUtility,
    private val getCurrentChangelogsForCpi: (EntityManager, String, String, String) -> List<CpkDbChangeLogEntity> =
        ::findCurrentCpkChangeLogsForCpi,
    private val virtualNodeRepository: VirtualNodeRepository = VirtualNodeRepositoryImpl()
) : VirtualNodeAsyncOperationHandler<VirtualNodeUpgradeRequest> {

    private companion object {
        val log = contextLogger()
    }

    override fun handle(
        requestTimestamp: Instant,
        requestId: String,
        request: VirtualNodeUpgradeRequest
    ): Record<*, *>? {
        log.info("Virtual node upgrade operation requested by ${request.actor} at $requestTimestamp: $request ")
        request.validateFields()

        upgradeVirtualNodeCpi(requestId, request.virtualNodeShortHash, request.cpiFileChecksum)

        return null
    }

    private fun upgradeVirtualNodeCpi(
        requestId: String,
        virtualNodeShortHash: String,
        targetCpiFileChecksum: String
    ) {
        val transactionCompleted = entityManagerFactory.createEntityManager().transaction { em ->

            val currentVirtualNode = findCurrentVirtualNode(em, virtualNodeShortHash)

            if (currentVirtualNode.vaultDbOperationalStatus != OperationalStatus.INACTIVE) {
                // a future iteration of this will check first to see if migrations are actually required
                throw VirtualNodeStateException("Virtual node must be in maintenance before upgrade (request $requestId).")
            }

            val targetCpiMetadata = findTargetCpi(targetCpiFileChecksum)
            val originalCpiMetadata = findCurrentCpi(
                em,
                currentVirtualNode.cpiIdentifier.name,
                currentVirtualNode.cpiIdentifier.version,
                currentVirtualNode.cpiIdentifier.signerSummaryHash.toString()
            )

            validateCpiInSameGroup(originalCpiMetadata, targetCpiMetadata)

            val cpiName = targetCpiMetadata.id.name
            val cpiVersion = targetCpiMetadata.id.version
            val cpiSignerSummaryHash = targetCpiMetadata.id.signerSummaryHash.toString()
            val upgradedVnodeInfo =
                virtualNodeRepository.upgradeVirtualNodeCpi(em, virtualNodeShortHash, cpiName, cpiVersion, cpiSignerSummaryHash)

            val migrationChangelogs: Map<String, List<CpkDbChangeLogEntity>> =
                getCurrentChangelogsForCpi(em, cpiName, cpiVersion, cpiSignerSummaryHash)
                    .groupBy { it.id.cpkFileChecksum }

            UpgradeTransactionCompleted(
                upgradedVnodeInfo,
                migrationChangelogs,
                upgradedVnodeInfo.vaultDdlConnectionId
            )
        }

        if (transactionCompleted.vaultDdlConnectionId != null) {
            log.info("Virtual node upgrade (request $requestId) vault DDL connection found, running CPI migrations.")
            migrationUtility.runCpiMigrations(
                ShortHash.of(virtualNodeShortHash),
                transactionCompleted.migrationsByCpkFileChecksum,
                transactionCompleted.vaultDdlConnectionId
            )
            log.info("Virtual node upgrade (request $requestId) CPI migrations completed.")
        }

        val upgradedVNodeInfo = transactionCompleted.upgradedVirtualNodeInfo
        virtualNodeInfoPublisher.publish(
            listOf(
                Record(
                    Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC,
                    upgradedVNodeInfo.holdingIdentity.toAvro(),
                    upgradedVNodeInfo.toAvro()
                )
            )
        )

        log.info(
            "Virtual node upgrade complete ($requestId) - Virtual node " +
                    "${upgradedVNodeInfo.holdingIdentity.shortHash} successfully upgraded to CPI " +
                    "name: ${upgradedVNodeInfo.cpiIdentifier.name}, version: ${upgradedVNodeInfo.cpiIdentifier.version}"
        )
    }

    data class UpgradeTransactionCompleted(
        val upgradedVirtualNodeInfo: VirtualNodeInfo,
        val migrationsByCpkFileChecksum: Map<String, List<CpkDbChangeLogEntity>>,
        val vaultDdlConnectionId: UUID?,
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

    private fun VirtualNodeUpgradeRequest.validateFields() {
        requireNotNull(virtualNodeShortHash) {
            "Virtual node identifier is missing"
        }
        requireNotNull(cpiFileChecksum) {
            "CPI file checksum is missing"
        }
    }
}