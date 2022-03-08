package net.corda.flow.manager.impl.handlers.requests

import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.manager.FlowProcessingExceptionTypes.FLOW_FAILED
import net.corda.flow.manager.factory.FlowMessageFactory
import net.corda.flow.manager.fiber.FlowIORequest
import net.corda.flow.manager.impl.FlowEventContext
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("Unused")
@Component(service = [FlowRequestHandler::class])
class FlowFailedRequestHandler @Activate constructor(
    @Reference(service = FlowMessageFactory::class)
    private val flowMessageFactory: FlowMessageFactory
) : FlowRequestHandler<FlowIORequest.FlowFailed> {

    private companion object {
        val log = contextLogger()
    }

    override val type = FlowIORequest.FlowFailed::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.FlowFailed): WaitingFor {
        return WaitingFor(null)
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.FlowFailed): FlowEventContext<Any> {
        val checkpoint = requireCheckpoint(context)

        log.info("Flow [${checkpoint.flowKey.flowId}] failed", request.exception)

        val status = flowMessageFactory.createFlowFailedStatusMessage(
            checkpoint,
            FLOW_FAILED,
            request.exception.message ?: request.exception.javaClass.name
        )
        val record = Record(Schemas.Flow.FLOW_STATUS_TOPIC, status.key, status)

        return context.copy(checkpoint = null, outputRecords = context.outputRecords + record)
    }
}