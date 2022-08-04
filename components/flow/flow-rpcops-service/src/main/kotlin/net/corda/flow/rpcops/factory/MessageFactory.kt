package net.corda.flow.rpcops.factory

import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStatus
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.flow.rpcops.v1.types.response.FlowStatusResponse
import net.corda.flow.utils.KeyValueStore

interface MessageFactory {

    fun createStartFlowEvent(
        clientRequestId: String,
        virtualNode: VirtualNodeInfo,
        flowClassName: String,
        flowStartArgs: String,
        flowContextPlatformProperties: KeyValueStore
    ): FlowMapperEvent

    fun createFlowStatusResponse(flowStatus: FlowStatus): FlowStatusResponse

    fun createStartFlowStatus(
        clientRequestId: String,
        virtualNode: VirtualNodeInfo,
        flowClassName: String
    ): FlowStatus
}