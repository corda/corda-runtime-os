package net.corda.flow.rpcops.impl.factory

import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.FlowStatusKey
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.flow.rpcops.factory.MessageFactory
import net.corda.flow.rpcops.v1.response.HTTPFlowStateErrorResponse
import net.corda.flow.rpcops.v1.response.HTTPFlowStatusResponse
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Component
import java.time.Instant

@Component(immediate = true, service = [MessageFactory::class])
class MessageFactoryImpl : MessageFactory {

    override fun createStartFlowEvent(
        clientRequestId: String,
        virtualNode: VirtualNodeInfo,
        flowClassName: String,
        flowStartArgs: String
    ): FlowMapperEvent {
        val context = FlowStartContext(
            FlowStatusKey(clientRequestId, virtualNode.holdingIdentity),
            FlowInitiatorType.RPC,
            clientRequestId,
            virtualNode.holdingIdentity,
            virtualNode.cpiIdentifier.name,
            virtualNode.holdingIdentity,
            flowClassName,
            Instant.now()
        )

        val startFlowEvent = StartFlow(context, flowStartArgs)
        return FlowMapperEvent(startFlowEvent)
    }

    override fun createFlowStatusResponse(flowStatus: FlowStatus): HTTPFlowStatusResponse {

        return HTTPFlowStatusResponse(
            flowStatus.key.identity.toCorda().id,
            flowStatus.key.id,
            flowStatus.flowId,
            flowStatus.flowStatus.toString(),
            flowStatus.result,
            if (flowStatus.error != null) HTTPFlowStateErrorResponse(
                flowStatus.error.errorType,
                flowStatus.error.errorMessage
            ) else null,
            Instant.now()
        )
    }

    override fun createStartFlowStatus(
        clientRequestId: String,
        virtualNode: VirtualNodeInfo,
        flowClassName: String
    ): FlowStatus {
        val now = Instant.now()
        return FlowStatus().apply {
            this.key = FlowStatusKey(clientRequestId, virtualNode.holdingIdentity)
            this.initiatorType = FlowInitiatorType.RPC
            this.flowClassName = flowClassName
            this.flowStatus = FlowStates.START_REQUESTED
            this.createdTimestamp = now
            this.lastUpdateTimestamp = now
        }
    }
}
