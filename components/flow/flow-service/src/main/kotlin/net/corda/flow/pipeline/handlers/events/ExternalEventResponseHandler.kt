package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.flow.external.events.impl.ExternalEventManager
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowEventHandler::class])
class ExternalEventResponseHandler @Activate constructor(
    @Reference(service = ExternalEventManager::class)
    private val externalEventManager: ExternalEventManager
) : FlowEventHandler<ExternalEventResponse> {

    private companion object {
        val log = contextLogger()
    }

    override val type = ExternalEventResponse::class.java

    override fun preProcess(context: FlowEventContext<ExternalEventResponse>): FlowEventContext<ExternalEventResponse> {
        val checkpoint = context.checkpoint
        val externalEventResponse = context.inputEventPayload

        if (!checkpoint.doesExist) {
            log.info(
                "Received a ${ExternalEventResponse::class.simpleName} for flow [${context.inputEvent.flowId}] that " +
                        "does not exist. The event will be discarded. ${ExternalEventResponse::class.simpleName}: " +
                        externalEventResponse
            )
            throw FlowEventException(
                "ExternalEventResponseHandler received a ${ExternalEventResponse::class.simpleName} for flow [${context.inputEvent.flowId}]" +
                        " that does not exist"
            )
        }

        val externalEventState = checkpoint.externalEventState

        if (externalEventState == null) {
            log.info(
                "Received an ${ExternalEventResponse::class.simpleName} with request id: " +
                        "${externalEventResponse.requestId} while flow [${context.inputEvent.flowId} is not waiting " +
                        "for an ${ExternalEventResponse::class.simpleName}. " +
                        "${ExternalEventResponse::class.simpleName}: $externalEventResponse"
            )
            throw FlowEventException(
                "ExternalEventResponseHandler received an ${ExternalEventResponse::class.simpleName} with request id: " +
                        "${externalEventResponse.requestId} while flow [${context.inputEvent.flowId} is not waiting " +
                        "for an ${ExternalEventResponse::class.simpleName}"
            )
        }

        checkpoint.externalEventState = externalEventManager.processResponse(
            externalEventState,
            externalEventResponse
        )

        return context
    }
}