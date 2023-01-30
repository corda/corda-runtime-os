package net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers

import net.corda.data.virtualnode.VirtualNodeUpgradeRequest
import net.corda.libs.virtualnode.common.exception.CpiNotFoundException
import net.corda.libs.virtualnode.datamodel.VirtualNodeNotFoundException
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepositoryImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.orm.utils.transaction
import net.corda.schema.Schemas
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.write.db.impl.writer.CpiMetadataLite
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeEntityRepository
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.VirtualNodeAsyncOperationHandler
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.exception.MgmGroupMismatchException
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import org.slf4j.LoggerFactory

internal class VirtualNodeUpgradeOperationHandler(
    private val entityManagerFactory: EntityManagerFactory,
    private val oldVirtualNodeEntityRepository: VirtualNodeEntityRepository,
    private val virtualNodeInfoPublisher: Publisher,
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
        request.validateFields()

        upgradeVirtualNodeCpi(requestId, request.virtualNodeShortHash, request.cpiFileChecksum)

        return null
    }

    private fun upgradeVirtualNodeCpi(
        requestId: String,
        virtualNodeShortHash: String,
        targetCpiFileChecksum: String
    ) {
        val vnodeInfo = entityManagerFactory.createEntityManager().transaction { em ->

            val currentVirtualNode = findCurrentVirtualNode(em, virtualNodeShortHash)
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
            virtualNodeRepository.upgradeVirtualNodeCpi(
                em,
                virtualNodeShortHash,
                cpiName,
                cpiVersion,
                cpiSignerSummaryHash
            )
        }

        virtualNodeInfoPublisher.publish(
            listOf(
                Record(
                    Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC,
                    vnodeInfo.holdingIdentity.toAvro(),
                    vnodeInfo.toAvro()
                )
            )
        )

        logger.info(
            "Virtual node upgrade complete ($requestId) - Virtual node " +
                    "${vnodeInfo.holdingIdentity.shortHash} successfully upgraded to CPI " +
                    "name: ${vnodeInfo.cpiIdentifier.name}, version: ${vnodeInfo.cpiIdentifier.version}"
        )
    }


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