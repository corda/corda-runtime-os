package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.flow.external.events.impl.ExternalEventManager
import net.corda.flow.pipeline.FlowEventContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowEventHandler::class])
class FlowExternalEventHandler @Activate constructor(
    @Reference(service = ExternalEventManager::class)
    private val externalEventManager: ExternalEventManager
) : FlowEventHandler<ExternalEventResponse> {

    override val type = ExternalEventResponse::class.java

    override fun preProcess(context: FlowEventContext<ExternalEventResponse>): FlowEventContext<ExternalEventResponse> {
        val checkpoint = context.checkpoint
        val externalEventResponse = context.inputEventPayload
        val externalEventState = checkpoint.externalEventState
        if (externalEventState == null) {
            // do something, probably discard the event
        } else {
            checkpoint.externalEventState = externalEventManager.processEventReceived(
                externalEventState,
                externalEventResponse
            )
        }
        return context
    }
}