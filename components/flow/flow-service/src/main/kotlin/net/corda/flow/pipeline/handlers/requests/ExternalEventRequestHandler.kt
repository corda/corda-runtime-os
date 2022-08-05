package net.corda.flow.pipeline.handlers.requests

import java.time.Instant
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.flow.state.waiting.external.ExternalEventResponse
import net.corda.flow.external.events.impl.ExternalEventManager
import net.corda.flow.external.events.impl.handler.ExternalEventHandlerMap
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class ExternalEventRequestHandler @Activate constructor(
    @Reference(service = ExternalEventManager::class)
    private val externalEventManager: ExternalEventManager,
    @Reference(service = ExternalEventHandlerMap::class)
    private val externalEventHandlerMap: ExternalEventHandlerMap
) : FlowRequestHandler<FlowIORequest.ExternalEvent> {

    override val type = FlowIORequest.ExternalEvent::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.ExternalEvent): WaitingFor {
        return WaitingFor(ExternalEventResponse(request.requestId))
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.ExternalEvent): FlowEventContext<Any> {
        val flowExternalEventContext = ExternalEventContext.newBuilder()
            .setRequestId(request.requestId)
            .setFlowId(context.checkpoint.flowId)
            .build()

        val eventRecord = externalEventHandlerMap.get(request.handlerClass.name)
            .suspending(context.checkpoint, flowExternalEventContext, request.parameters)

        context.checkpoint.externalEventState = externalEventManager.processEventToSend(
            context.checkpoint.flowId,
            request.requestId,
            request.handlerClass.name,
            eventRecord,
            Instant.now()
        )
        return context
    }
}