package net.corda.virtualnode.rpcops.common

import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.lifecycle.Lifecycle

interface VirtualNodeSenderService : Lifecycle {
    fun sendAndReceive(request: VirtualNodeManagementRequest): VirtualNodeManagementResponse
}
