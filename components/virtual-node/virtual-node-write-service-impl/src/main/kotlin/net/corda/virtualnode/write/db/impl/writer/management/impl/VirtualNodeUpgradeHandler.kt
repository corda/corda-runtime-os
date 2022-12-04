package net.corda.virtualnode.write.db.impl.writer.management.impl

import java.time.Instant
import java.util.concurrent.CompletableFuture
import net.corda.data.ExceptionEnvelope
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.data.virtualnode.VirtualNodeUpgradeRequest
import net.corda.libs.virtualnode.common.exception.CpiNotFoundException
import net.corda.libs.virtualnode.common.exception.HoldingIdentityNotFoundException
import net.corda.libs.virtualnode.common.exception.MgmGroupMismatchException
import net.corda.libs.virtualnode.common.exception.VirtualNodeNotFoundException
import net.corda.utilities.time.Clock
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.write.db.impl.writer.CpiMetadataLite
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeEntityRepository
import net.corda.virtualnode.write.db.impl.writer.management.VirtualNodeManagementHandler
import net.corda.virtualnode.write.db.impl.writer.management.common.VirtualNodeInfoRecordPublisher

internal class VirtualNodeUpgradeHandler(
    private val virtualNodeEntityRepository: VirtualNodeEntityRepository,
    private val virtualNodeInfoPublisher: VirtualNodeInfoRecordPublisher,
    private val clock: Clock,
) : VirtualNodeManagementHandler<VirtualNodeUpgradeRequest> {

    private companion object {
        val logger = contextLogger()
    }

    override fun handle(
        requestTimestamp: Instant,
        request: VirtualNodeUpgradeRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {
        upgradeVirtualNodeCpi(requestTimestamp, request, respFuture)
    }

    private fun upgradeVirtualNodeCpi(
        requestTimestamp: Instant,
        request: VirtualNodeUpgradeRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {
        try {
            logger.info("Starting upgrade requested at $requestTimestamp")
            request.validateFields()

            // todo work out which of these operations can be condensed into one transaction

            val currentVirtualNode = findCurrentVirtualNode(request.virtualNodeShortHash)

            val upgradeCpiMetadata = findUpgradeCpi(request.cpiFileChecksum)
            val (holdingId, connections) = findHoldingIdentityAndConnections(request.virtualNodeShortHash)

            val originalCpiMetadata = findCurrentCpiMetadata(currentVirtualNode.cpiName, currentVirtualNode.cpiVersion)

            validateCpiInSameGroup(originalCpiMetadata, upgradeCpiMetadata)

            virtualNodeEntityRepository.updateVirtualNodeCpi(holdingId, upgradeCpiMetadata.id)

            virtualNodeInfoPublisher.publishVNodeInfo(holdingId, upgradeCpiMetadata, connections, clock.instant())

        } catch (e: Exception) {
            handleException(respFuture, e)
        }
    }



    private fun findCurrentCpiMetadata(cpiName: String, cpiVersion: String): CpiMetadataLite {
        return requireNotNull(virtualNodeEntityRepository.getCPIMetadataByNameAndVersion(cpiName, cpiVersion)) {
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

    private fun findCurrentVirtualNode(holdingIdentityShortHash: String): VirtualNodeEntityRepository.VirtualNodeLite {
        return virtualNodeEntityRepository.findByHoldingIdentity(holdingIdentityShortHash)
            ?: throw VirtualNodeNotFoundException(holdingIdentityShortHash)
    }

    private fun findHoldingIdentityAndConnections(holdingIdentityShortHash: String): VirtualNodeEntityRepository.HoldingIdentityAndConnections {
        return virtualNodeEntityRepository.getHoldingIdentityAndConnections(holdingIdentityShortHash)
            ?: throw HoldingIdentityNotFoundException(holdingIdentityShortHash)
    }

    private fun findUpgradeCpi(cpiFileChecksum: String): CpiMetadataLite {
        return virtualNodeEntityRepository.getCpiMetadataByChecksum(cpiFileChecksum)
            ?: throw CpiNotFoundException("CPI with file checksum $cpiFileChecksum was not found.")
    }

    // todo duplicate of VirtualNodeWriterProcessor
    private fun handleException(respFuture: CompletableFuture<VirtualNodeManagementResponse>, e: Exception) {
        logger.error("Error while processing virtual node request: ${e.message}", e)
        val response = VirtualNodeManagementResponse(
            clock.instant(),
            VirtualNodeManagementResponseFailure(
                ExceptionEnvelope().apply {
                    errorType = e::class.java.name
                    errorMessage = e.message
                }
            )
        )
        respFuture.complete(response)
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