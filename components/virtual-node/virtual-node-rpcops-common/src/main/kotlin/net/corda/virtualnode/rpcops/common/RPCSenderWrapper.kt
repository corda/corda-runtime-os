package net.corda.virtualnode.rpcops.common

import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse

interface RPCSenderWrapper : AutoCloseable {
    fun sendAndReceive(request: VirtualNodeManagementRequest): VirtualNodeManagementResponse
}
