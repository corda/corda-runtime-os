package net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers

import net.corda.data.virtualnode.VirtualNodeUpgradeRequest
import net.corda.libs.virtualnode.common.exception.CpiNotFoundException
import net.corda.libs.virtualnode.datamodel.VirtualNodeNotFoundException
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepositoryImpl
import net.corda.messaging.api.records.Record
import net.corda.orm.utils.transaction
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.write.db.impl.writer.CpiMetadataLite
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeEntityRepository
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.VirtualNodeAsyncOperationHandler
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.exception.MgmGroupMismatchException
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

internal class VirtualNodeUpgradeOperationHandler(
    private val entityManagerFactory: EntityManagerFactory,
    private val oldVirtualNodeEntityRepository: VirtualNodeEntityRepository,
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
        log.info("Virtual node upgrade operation $request. requested at $requestTimestamp")
        upgradeVirtualNodeCpi(requestId, request)
        return null
    }

    private fun upgradeVirtualNodeCpi(
        requestId: String,
        request: VirtualNodeUpgradeRequest
    ) {
        try {
            request.validateFields()

            entityManagerFactory.createEntityManager().transaction { em ->

                val currentVirtualNode = findCurrentVirtualNode(em, request.virtualNodeShortHash)
                val targetCpiMetadata = findUpgradeCpi(request.cpiFileChecksum)
                val originalCpiMetadata = findCurrentCpiMetadata(
                    currentVirtualNode.cpiIdentifier.name, currentVirtualNode.cpiIdentifier.version
                )

                validateCpiInSameGroup(originalCpiMetadata, targetCpiMetadata)

                val updatedVirtualNode = virtualNodeRepository.upgradeVirtualNodeCpi(
                    em,
                    currentVirtualNode.holdingIdentity.shortHash.toString(),
                    targetCpiMetadata.id.name,
                    targetCpiMetadata.id.version,
                    targetCpiMetadata.id.signerSummaryHash.toString()
                )

                log.info(
                    "Virtual node upgrade request $requestId successful. Virtual node " +
                            "${updatedVirtualNode.holdingIdentity.shortHash} successfully upgraded to CPI " +
                            "${targetCpiMetadata.id.name}, ${targetCpiMetadata.id.version} (${targetCpiMetadata.fileChecksum}"
                )

                // todo cs - publish virtual node info

            }
        } catch (e: Exception) {
            log.error("Error upgrading virtual node (request ID: $requestId) to cpi ${request.cpiFileChecksum}", e)
        }
    }


    private fun findCurrentCpiMetadata(cpiName: String, cpiVersion: String): CpiMetadataLite {
        return requireNotNull(oldVirtualNodeEntityRepository.getCPIMetadataByNameAndVersion(cpiName, cpiVersion)) {
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


    private fun findUpgradeCpi(cpiFileChecksum: String): CpiMetadataLite {
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