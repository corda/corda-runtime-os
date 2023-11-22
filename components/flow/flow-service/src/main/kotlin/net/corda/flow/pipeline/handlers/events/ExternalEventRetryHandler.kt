package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.event.external.ExternalEventRetry
import net.corda.data.flow.state.external.ExternalEventStateType
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.utilities.debug
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory

@Component(service = [FlowEventHandler::class])
class ExternalEventRetryHandler : FlowEventHandler<ExternalEventRetry> {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val type = ExternalEventRetry::class.java

    override fun preProcess(context: FlowEventContext<ExternalEventRetry>): FlowEventContext<ExternalEventRetry> {
        if (!context.checkpoint.doesExist) {
            log.debug {
                "Received a ${ExternalEventRetry::class.simpleName} for flow [${context.inputEvent.flowId}] that does not exist. " +
                        "The event will be discarded."
            }
            throw FlowEventException(
                "ExternalEventRetryHandler received a ${ExternalEventRetry::class.simpleName} for flow [${context.inputEvent.flowId}] that " +
                        "does not exist"
            )
        }

        val externalEventState = context.checkpoint.externalEventState
        if (externalEventState == null) {
            log.debug {
                "Received a ${ExternalEventRetry::class.simpleName} for flow [${context.inputEvent.flowId}] that does not have an external " +
                        "event state. The retry event must have been sent without writing the change to the checkpoint."
            }
            throw FlowEventException(
                "ExternalEventRetryHandler received a ${ExternalEventRetry::class.simpleName} for flow [${context.inputEvent.flowId}] that " +
                        "does not have an external event state."
            )
        }

        val status = externalEventState.status
        if (status == null) {
            log.debug {
                "Received a ${ExternalEventRetry::class.simpleName} for flow [${context.inputEvent.flowId}] that does not have an external " +
                        "event state status. The retry event must have been sent without writing the change to the checkpoint."
            }
            throw FlowEventException(
                "ExternalEventRetryHandler received a ${ExternalEventRetry::class.simpleName} for flow [${context.inputEvent.flowId}] that " +
                        "does not have an external event state."
            )
        }

        if (status.type == null || status.type != ExternalEventStateType.RETRY) {
            log.debug {
                "Received a ${ExternalEventRetry::class.simpleName} for flow [${context.inputEvent.flowId}] that does not have an external " +
                        "event state status of RETRY. A transient external event retry must have set the external event state to RETRY."
            }
            throw FlowEventException(
                "ExternalEventRetryHandler received a ${ExternalEventRetry::class.simpleName} for flow [${context.inputEvent.flowId}] that " +
                        "does not have an externalEventStateStatus of RETRY."
            )
        }

        externalEventState.retries = context.inputEventPayload.retries
        status.type = ExternalEventStateType.RETRYING
        return context
    }
}
