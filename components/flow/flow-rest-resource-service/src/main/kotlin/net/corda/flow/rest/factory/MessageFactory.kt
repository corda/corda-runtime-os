package net.corda.flow.rest.factory

import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStatus
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.flow.rest.v1.types.response.FlowResultResponse
import net.corda.flow.rest.v1.types.response.FlowStatusResponse
import net.corda.rest.response.ResponseEntity

interface MessageFactory {

    fun createStartFlowEvent(
        clientRequestId: String,
        virtualNode: VirtualNodeInfo,
        flowClassName: String,
        flowStartArgs: String,
        flowContextPlatformProperties: Map<String, String>
    ): FlowMapperEvent

    fun createFlowStatusResponse(flowStatus: FlowStatus): FlowStatusResponse

    fun createStartFlowStatus(
        clientRequestId: String,
        virtualNode: VirtualNodeInfo,
        flowClassName: String
    ): FlowStatus

    fun createFlowResultResponse(flowStatus: FlowStatus): ResponseEntity<FlowResultResponse>
}