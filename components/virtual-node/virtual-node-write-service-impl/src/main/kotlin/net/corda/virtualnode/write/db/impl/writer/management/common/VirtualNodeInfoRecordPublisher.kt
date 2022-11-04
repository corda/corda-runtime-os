package net.corda.virtualnode.write.db.impl.writer.management.common

import java.time.Instant
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.write.db.impl.writer.CpiMetadataLite
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbConnections

internal interface VirtualNodeInfoRecordPublisher {
    fun publishVNodeInfo(
        holdingIdentity: HoldingIdentity,
        cpiMetadata: CpiMetadataLite,
        dbConnections: VirtualNodeDbConnections,
        completedInstant: Instant
    )
}