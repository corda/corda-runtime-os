package net.corda.virtualnode.write.db.impl.writer.management.impl

import java.time.Instant
import java.util.concurrent.CompletableFuture
import net.corda.data.ExceptionEnvelope
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.data.virtualnode.VirtualNodeStateChangeRequest
import net.corda.data.virtualnode.VirtualNodeStateChangeResponse
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.common.exception.CpiNotFoundException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeEntityRepository
import net.corda.virtualnode.write.db.impl.writer.management.VirtualNodeManagementHandler

internal class ChangeVirtualNodeStateHandler(
    private val vnodePublisher: Publisher,
    private val virtualNodeEntityRepository: VirtualNodeEntityRepository,
) : VirtualNodeManagementHandler<VirtualNodeStateChangeRequest> {

    override fun handle(
        requestTimestamp: Instant,
        request: VirtualNodeStateChangeRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {
        try {
            changeVirtualNodeState(requestTimestamp, request, respFuture)
        } catch (e: Exception) {
            respFuture.complete(
                VirtualNodeManagementResponse(
                    requestTimestamp,
                    VirtualNodeManagementResponseFailure(
                        ExceptionEnvelope(
                            e::class.java.name,
                            e.message
                        )
                    )
                )
            )
        }
    }

    private fun changeVirtualNodeState(
        requestTimestamp: Instant,
        stateChangeRequest: VirtualNodeStateChangeRequest,
        respFuture: CompletableFuture<VirtualNodeManagementResponse>
    ) {

        val updatedVirtualNodeEntity = virtualNodeEntityRepository.setVirtualNodeState(
            stateChangeRequest.holdingIdentityShortHash,
            stateChangeRequest.newState
        )

        val virtualNodeInfo = with(updatedVirtualNodeEntity) {
            val cpiMetadata = virtualNodeEntityRepository.getCPIMetadataByNameAndVersion(
                this.cpiName,
                this.cpiVersion
            ) ?: throw CpiNotFoundException(
                "No corresponding meta data found for cpi for ${this.holdingIdentity.holdingIdentityShortHash}"
            )
            val holdingIdentity = HoldingIdentity(
                net.corda.v5.base.types.MemberX500Name.parse(this.holdingIdentity.x500Name),
                this.holdingIdentity.mgmGroupId
            )
            val cpiIdentifier = CpiIdentifier(
                cpiMetadata.id.name,
                cpiMetadata.id.version,
                cpiMetadata.id.signerSummaryHash
            )
            VirtualNodeInfo(
                holdingIdentity,
                cpiIdentifier,
                this.holdingIdentity.vaultDDLConnectionId,
                this.holdingIdentity.vaultDMLConnectionId!!,
                this.holdingIdentity.cryptoDDLConnectionId,
                this.holdingIdentity.cryptoDMLConnectionId!!,
                this.holdingIdentity.uniquenessDDLConnectionId,
                this.holdingIdentity.uniquenessDMLConnectionId!!,
                this.holdingIdentity.hsmConnectionId,
                net.corda.virtualnode.VirtualNodeState.valueOf(this.virtualNodeState),
                this.entityVersion,
                this.insertTimestamp!!
            )
        }.toAvro()

        val virtualNodeRecord = Record(
            Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC,
            virtualNodeInfo.holdingIdentity,
            virtualNodeInfo
        )

        try {
            // TODO - CORE-3319 - Strategy for DB and Kafka retries.
            val future = vnodePublisher.publish(listOf(virtualNodeRecord)).first()

            // TODO - CORE-3730 - Define timeout policy.
            future.get()
        } catch (e: Exception) {
            throw VirtualNodeWriteServiceException(
                "Record $virtualNodeRecord was written to the database, but couldn't be published. Cause: $e", e
            )
        }

        val response = VirtualNodeManagementResponse(
            requestTimestamp,
            VirtualNodeStateChangeResponse(
                stateChangeRequest.holdingIdentityShortHash,
                stateChangeRequest.newState
            )
        )
        respFuture.complete(response)
    }
}