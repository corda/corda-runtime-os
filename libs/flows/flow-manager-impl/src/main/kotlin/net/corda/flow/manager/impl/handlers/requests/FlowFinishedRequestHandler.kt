package net.corda.flow.manager.impl.handlers.requests

import net.corda.flow.manager.impl.FlowEventContext
import net.corda.flow.statemachine.requests.FlowIORequest
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

@Component(service = [FlowRequestHandler::class])
class FlowFinishedRequestHandler : FlowRequestHandler<FlowIORequest.FlowFinished> {

    private companion object {
        val log = contextLogger()
    }

    override val type = FlowIORequest.FlowFinished::class.java

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.FlowFinished): FlowEventContext<Any> {
        val checkpoint = context.checkpoint!!
        log.info("Flow [${checkpoint.flowKey.flowId}] completed successfully")
        context.setCheckpointWaitingFor(null)
        // Needs to go to a different topic, but currently sends to the flow event topic
        // The commented out code should be added + changed when this is resolved.
//        val result = RPCFlowResult.newBuilder()
//            .setClientId(checkpoint.flowState.clientId)
//            .setFlowName(checkpoint.flowState.flowClassName)
//            // Set this at some point
//            .setCPIIdentifier(SecureHash("SHA-256", ByteBuffer.wrap(byteArrayOf())))
//            // Replace with JSON string rather than [toString]
//            .setResult(request.result.toString())
//            .setError(null)
//            .build()
//        val record = Record(
//            topic = Schemas.FLOW_EVENT_TOPIC,
//            key = checkpoint.flowKey,
//            value = FlowEvent(checkpoint.flowKey, checkpoint.cpiId, result)
//        )
//        val event = FlowEvent(checkpoint.flowKey, checkpoint.cpiId, result)
//        return context.copy(checkpoint = null, outputRecords = context.outputRecords + record)
        return context.copy(checkpoint = null)
    }
}