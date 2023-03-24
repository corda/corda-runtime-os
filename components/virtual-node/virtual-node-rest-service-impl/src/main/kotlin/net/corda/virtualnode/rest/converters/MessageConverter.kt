package net.corda.virtualnode.rest.converters

import net.corda.rest.asynchronous.v1.AsyncOperationStatus
import net.corda.data.virtualnode.VirtualNodeOperationStatus as AvroVirtualNodeOperationStatus

interface MessageConverter {

    fun convert(
        status: AvroVirtualNodeOperationStatus,
        operation: String,
        resourceId: String? = null,
    ): AsyncOperationStatus
}