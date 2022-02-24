package net.corda.flow.rpcops.factory

import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStatus
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.flow.rpcops.v1.response.HTTPFlowStatusResponse

interface MessageFactory {

    fun createStartFlowEvent(
        clientRequestId: String,
        virtualNode: VirtualNodeInfo,
        flowClassName: String,
        flowStartArgs: String
    ): FlowMapperEvent

    fun createFlowStatusResponse(flowStatus: FlowStatus): HTTPFlowStatusResponse

    fun createStartFlowStatus(
        clientRequestId: String,
        virtualNode: VirtualNodeInfo,
        flowClassName: String
    ): FlowStatus
}