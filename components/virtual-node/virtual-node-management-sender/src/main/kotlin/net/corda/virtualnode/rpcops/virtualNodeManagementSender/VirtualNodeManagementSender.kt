package net.corda.virtualnode.rpcops.virtualNodeManagementSender

import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import java.time.Duration

interface VirtualNodeManagementSender : AutoCloseable {
    val timeout: Duration
    fun sendAndReceive(request: VirtualNodeManagementRequest): VirtualNodeManagementResponse
}
