package net.corda.virtualnode.write.db.impl.writer.management.common

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.virtualnode.write.db.impl.writer.dto.VirtualNodeLite

internal interface VirtualNodeInfoRecordPublisher {
    fun publishVNodeInfo(updatedVirtualNode: VirtualNodeLite, cpiIdentifier: CpiIdentifier)
}