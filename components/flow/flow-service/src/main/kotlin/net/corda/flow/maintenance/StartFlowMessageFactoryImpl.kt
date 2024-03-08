package net.corda.flow.maintenance

import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.flow.utils.keyValuePairListOf
import java.time.Instant

// copy of net.corda.flow.rest.impl.factory.MessageFactoryImpl
class StartFlowMessageFactoryImpl {

    fun createStartFlowEvent(
        clientRequestId: String,
        virtualNode: VirtualNodeInfo,
        flowClassName: String,
        flowStartArgs: String,
        flowContextPlatformProperties: Map<String, String>
    ): FlowMapperEvent {
        val context = FlowStartContext(
            FlowKey(clientRequestId, virtualNode.holdingIdentity),
            FlowInitiatorType.RPC,
            clientRequestId,
            virtualNode.holdingIdentity,
            virtualNode.cpiIdentifier.name,
            virtualNode.holdingIdentity,
            flowClassName,
            flowStartArgs,
            keyValuePairListOf(flowContextPlatformProperties),
            Instant.now()
        )

        val startFlowEvent = StartFlow(context, flowStartArgs)
        return FlowMapperEvent(startFlowEvent)
    }

    fun createStartFlowStatus(
        clientRequestId: String,
        virtualNode: VirtualNodeInfo,
        flowClassName: String
    ): FlowStatus {
        val now = Instant.now()
        return FlowStatus().apply {
            this.key = FlowKey(clientRequestId, virtualNode.holdingIdentity)
            this.initiatorType = FlowInitiatorType.RPC // needs to change to scheduled
            this.flowClassName = flowClassName
            this.flowStatus = FlowStates.START_REQUESTED
            this.createdTimestamp = now
            this.lastUpdateTimestamp = now
        }
    }
}
