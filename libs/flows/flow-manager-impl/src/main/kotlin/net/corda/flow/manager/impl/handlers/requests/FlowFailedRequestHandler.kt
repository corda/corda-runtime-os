package net.corda.flow.manager.impl.handlers.requests

import net.corda.flow.manager.FlowEventContext
import net.corda.flow.manager.impl.handlers.requests.FlowRequestHandler
import net.corda.flow.manager.impl.handlers.requests.setCheckpointFlowIORequest
import net.corda.flow.statemachine.requests.FlowIORequest
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

@Component(service = [FlowRequestHandler::class])
class FlowFailedRequestHandler : FlowRequestHandler<FlowIORequest.FlowFailed> {

    private companion object {
        val log = contextLogger()
    }

    override val type = FlowIORequest.FlowFailed::class.java

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.FlowFailed): FlowEventContext<Any> {
        val checkpoint = context.checkpoint!!
        log.info("Flow [${checkpoint.flowKey.flowId}] failed", request.exception)
        context.setCheckpointFlowIORequest(null)
        // Needs to go to a different topic, but currently sends to the flow event topic
        // The commented out code should be added + changed when this is resolved.
        // Needs to handle sending errors when there are initiated sessions.
//        val result = RPCFlowResult.newBuilder()
//            .setClientId(checkpoint.flowState.clientId)
//            .setFlowName(checkpoint.flowState.flowClassName)
//            // Set this at some point
//            .setCPIIdentifier(SecureHash("SHA-256", ByteBuffer.wrap(byteArrayOf())))
//            .setResult(null)
//            .setError(ExceptionEnvelope("500", request.exception.message))
//            .build()
//        val record = Record(
//            topic = Schemas.FLOW_EVENT_TOPIC,
//            key = checkpoint.flowKey,
//            value = FlowEvent(checkpoint.flowKey, checkpoint.cpiId, result)
//        )
//        return context.copy(checkpoint = null, outputRecords = context.outputRecords + record)
        return context.copy(checkpoint = null)
    }
}