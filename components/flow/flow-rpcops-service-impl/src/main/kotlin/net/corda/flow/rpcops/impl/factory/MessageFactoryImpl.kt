package net.corda.flow.rpcops.impl.factory

import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.flow.rpcops.factory.MessageFactory
import net.corda.flow.rpcops.v1.types.response.FlowStateErrorResponse
import net.corda.flow.rpcops.v1.types.response.FlowStatusResponse
import net.corda.flow.utils.keyValuePairListOf
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Component
import java.time.Instant

@Component(immediate = true, service = [MessageFactory::class])
class MessageFactoryImpl : MessageFactory {

    override fun createStartFlowEvent(
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

    override fun createFlowStatusResponse(flowStatus: FlowStatus): FlowStatusResponse {

        return FlowStatusResponse(
            flowStatus.key.identity.toCorda().shortHash.value,
            flowStatus.key.id,
            flowStatus.flowId,
            flowStatus.flowStatus.toString(),
            flowStatus.result,
            if (flowStatus.error != null) FlowStateErrorResponse(
                flowStatus.error.errorType,
                flowStatus.error.errorMessage
            ) else null,
            flowStatus.lastUpdateTimestamp
        )
    }

    override fun createStartFlowStatus(
        clientRequestId: String,
        virtualNode: VirtualNodeInfo,
        flowClassName: String
    ): FlowStatus {
        val now = Instant.now()
        return FlowStatus().apply {
            this.key = FlowKey(clientRequestId, virtualNode.holdingIdentity)
            this.initiatorType = FlowInitiatorType.RPC
            this.flowClassName = flowClassName
            this.flowStatus = FlowStates.START_REQUESTED
            this.createdTimestamp = now
            this.lastUpdateTimestamp = now
        }
    }
}
