package net.corda.virtualnode.rest.converters

import net.corda.rest.asynchronous.v1.AsyncOperationStatus
import net.corda.data.virtualnode.VirtualNodeOperationStatus as AvroVirtualNodeOperationStatus
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeInfo as VirtualNodeInfoRestResponse
import net.corda.virtualnode.VirtualNodeInfo as VirtualNodeInfoDto

interface MessageConverter {
    fun convert(virtualNodeInfoDto: VirtualNodeInfoDto): VirtualNodeInfoRestResponse

    fun convert(
        status: AvroVirtualNodeOperationStatus,
        operation: String,
        resourceId: String? = null,
    ): AsyncOperationStatus
}
