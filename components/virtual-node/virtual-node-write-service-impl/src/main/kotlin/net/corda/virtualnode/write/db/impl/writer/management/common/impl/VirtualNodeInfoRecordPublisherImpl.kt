package net.corda.virtualnode.write.db.impl.writer.management.common.impl

import java.time.Instant
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.CpiMetadataLite
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbConnections
import net.corda.virtualnode.write.db.impl.writer.management.common.VirtualNodeInfoRecordPublisher

internal class VirtualNodeInfoRecordPublisherImpl(
    private val vnodePublisher: Publisher
) : VirtualNodeInfoRecordPublisher {

    override fun publishVNodeInfo(
        holdingIdentity: HoldingIdentity,
        cpiMetadata: CpiMetadataLite,
        dbConnections: VirtualNodeDbConnections,
        completedInstant: Instant
    ) {
        val virtualNodeRecord = createVirtualNodeRecord(holdingIdentity, cpiMetadata, dbConnections, completedInstant)
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
        holdingIdentity: HoldingIdentity,
        cpiMetadata: CpiMetadataLite,
        dbConnections: VirtualNodeDbConnections,
        completedInstant: Instant
    ): Record<net.corda.data.identity.HoldingIdentity, VirtualNodeInfo> {

        val cpiIdentifier = CpiIdentifier(cpiMetadata.id.name, cpiMetadata.id.version, cpiMetadata.id.signerSummaryHash)
        val virtualNodeInfo = with(dbConnections) {
            net.corda.virtualnode.VirtualNodeInfo(
                holdingIdentity,
                cpiIdentifier,
                vaultDdlConnectionId,
                vaultDmlConnectionId,
                cryptoDdlConnectionId,
                cryptoDmlConnectionId,
                uniquenessDdlConnectionId,
                uniquenessDmlConnectionId,
                timestamp = completedInstant,
                state = net.corda.virtualnode.VirtualNodeInfo.DEFAULT_INITIAL_STATE
            )
                .toAvro()
        }
        return Record(Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC, virtualNodeInfo.holdingIdentity, virtualNodeInfo)
    }
}