package net.corda.virtualnode.write.db.impl.writer.management.common.impl

import java.time.Instant
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.dto.VirtualNodeLite
import net.corda.virtualnode.write.db.impl.writer.management.common.VirtualNodeInfoRecordPublisher

internal class VirtualNodeInfoRecordPublisherImpl(
    private val vnodePublisher: Publisher
) : VirtualNodeInfoRecordPublisher {

    override fun publishVNodeInfo(updatedVirtualNode: VirtualNodeLite, cpiIdentifier: CpiIdentifier) {
        val virtualNodeRecord = createVirtualNodeRecord(updatedVirtualNode, cpiIdentifier)
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
    }

    private fun createVirtualNodeRecord(
        updatedVirtualNode: VirtualNodeLite,
        cpiIdentifier: CpiIdentifier
    ): Record<net.corda.data.identity.HoldingIdentity, VirtualNodeInfo> {

        val avroVnodeInfo = with(updatedVirtualNode) {
            VirtualNodeInfo(
                net.corda.data.identity.HoldingIdentity(
                    holdingIdentity.holdingIdentityX500Name,
                    holdingIdentity.groupId
                ),
                cpiIdentifier.toAvro(),
                vaultDdlConnectionId,
                vaultDmlConnectionId,
                cryptoDdlConnectionId,
                cryptoDmlConnectionId,
                uniquenessDdlConnectionId,
                uniquenessDmlConnectionId,
                holdingIdentity.hsmConnectionId,
                flowP2pOperationalStatus,
                flowStartOperationalStatus,
                flowOperationalStatus,
                vaultDbOperationalStatus,
                entityVersion,
                creationTimestamp
            )
        }

        return Record(Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC, avroVnodeInfo.holdingIdentity, avroVnodeInfo)
    }
}