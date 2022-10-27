package net.corda.virtualnode.rpcops.common

import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.lifecycle.Resource
import java.time.Duration

/**
 * Interface for sending virtual node management operations, including synchronous and asynchronous operations.
 */
interface VirtualNodeSender : Resource {
    val timeout: Duration
    fun sendAndReceive(request: VirtualNodeManagementRequest): VirtualNodeManagementResponse
    fun sendAsynchronousOperation(operationRequest: VirtualNodeAsynchronousRequest)
}
