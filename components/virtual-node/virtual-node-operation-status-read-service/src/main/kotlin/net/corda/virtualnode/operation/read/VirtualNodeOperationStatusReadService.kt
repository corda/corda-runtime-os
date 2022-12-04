package net.corda.virtualnode.operation.read

import net.corda.data.virtualnode.VirtualNodeOperationStatus
import net.corda.lifecycle.Lifecycle

interface VirtualNodeOperationStatusReadService: Lifecycle {
    fun getByRequestId(requestId: String): VirtualNodeOperationStatus?
    fun getByVirtualNodeShortHash(virtualNodeShortHash: String): List<VirtualNodeOperationStatus>
}