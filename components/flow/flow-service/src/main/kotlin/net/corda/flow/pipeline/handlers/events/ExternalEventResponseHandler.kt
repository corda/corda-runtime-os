package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.state.external.ExternalEventStateType
import net.corda.flow.external.events.impl.ExternalEventManager
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.utilities.debug
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

@Component(service = [FlowEventHandler::class])
class ExternalEventResponseHandler(
    private val clock: Clock,
    private val externalEventManager: ExternalEventManager
) : FlowEventHandler<ExternalEventResponse> {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suppress("Unused")
    @Activate
    constructor(
        @Reference(service = ExternalEventManager::class)
        externalEventManager: ExternalEventManager
    ) : this(UTCClock(), externalEventManager)

    override val type = ExternalEventResponse::class.java

    override fun preProcess(context: FlowEventContext<ExternalEventResponse>): FlowEventContext<ExternalEventResponse> {
        val checkpoint = context.checkpoint
        val externalEventResponse = context.inputEventPayload

        if (!checkpoint.doesExist) {
            log.debug {
                "Received a ${ExternalEventResponse::class.simpleName} for flow [${context.inputEvent.flowId}] that " +
                        "does not exist. The event will be discarded. ${ExternalEventResponse::class.simpleName}: " +
                        externalEventResponse
            }
            throw FlowEventException(
                "ExternalEventResponseHandler received a ${ExternalEventResponse::class.simpleName} for flow" +
                        " [${context.inputEvent.flowId}] that does not exist"
            )
        }

        val externalEventState = checkpoint.externalEventState

        if (externalEventState == null) {
            log.debug {
                "Received an ${ExternalEventResponse::class.simpleName} with request id: " +
                        "${externalEventResponse.requestId} while flow [${context.inputEvent.flowId} is not waiting " +
                        "for an ${ExternalEventResponse::class.simpleName}. " +
                        "${ExternalEventResponse::class.simpleName}: $externalEventResponse"
            }
            throw FlowEventException(
                "ExternalEventResponseHandler received an ${ExternalEventResponse::class.simpleName} with request id: " +
                        "${externalEventResponse.requestId} while flow [${context.inputEvent.flowId} is not waiting " +
                        "for an ${ExternalEventResponse::class.simpleName}"
            )
        }

        val updatedExternalEventState = externalEventManager.processResponse(
            externalEventState,
            externalEventResponse
        )

        checkpoint.externalEventState = updatedExternalEventState

        if (updatedExternalEventState.status.type == ExternalEventStateType.RETRY) {
            val sleepDuration = Duration.between(clock.instant(), updatedExternalEventState.sendTimestamp).toMillis().toInt()
            checkpoint.setFlowSleepDuration(if (sleepDuration > 0) sleepDuration else 0)
        }

        return context
    }
}
