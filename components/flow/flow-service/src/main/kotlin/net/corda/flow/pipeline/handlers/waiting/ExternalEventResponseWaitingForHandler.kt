package net.corda.flow.pipeline.handlers.waiting

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.state.external.ExternalEventState
import net.corda.data.flow.state.external.ExternalEventStateType
import net.corda.flow.external.events.impl.ExternalEventManager
import net.corda.flow.external.events.impl.factory.ExternalEventFactoryMap
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.state.FlowCheckpoint
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.FlowConfig
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowWaitingForHandler::class])
class ExternalEventResponseWaitingForHandler @Activate constructor(
    @Reference(service = ExternalEventManager::class)
    private val externalEventManager: ExternalEventManager,
    @Reference(service = ExternalEventFactoryMap::class)
    private val externalEventFactoryMap: ExternalEventFactoryMap
) : FlowWaitingForHandler<net.corda.data.flow.state.waiting.external.ExternalEventResponse> {

    private companion object {
        val log = contextLogger()
    }

    override val type = net.corda.data.flow.state.waiting.external.ExternalEventResponse::class.java

    override fun runOrContinue(
        context: FlowEventContext<*>,
        waitingFor: net.corda.data.flow.state.waiting.external.ExternalEventResponse
    ): FlowContinuation {
        val externalEventState =
            context.checkpoint.externalEventState
                ?: throw FlowFatalException("Waiting for external event but state not set")

        val continuation = when (externalEventState.status.type) {
            ExternalEventStateType.OK -> {
                resumeIfResponseReceived(context.checkpoint, externalEventState)
            }
            ExternalEventStateType.RETRY -> {
                retryOrError(context.config, externalEventState.status.exception, externalEventState)
            }
            ExternalEventStateType.PLATFORM_ERROR -> {
                FlowContinuation.Error(CordaRuntimeException(externalEventState.status.exception.errorMessage))
            }
            ExternalEventStateType.FATAL_ERROR -> {
                throw FlowFatalException(externalEventState.status.exception.errorMessage)
            }
            null -> throw FlowFatalException(
                "Unexpected null ${ExternalEventStateType::class.java.name} for flow ${context.checkpoint.flowId}"
            )
        }

        if (continuation != FlowContinuation.Continue) {
            context.checkpoint.externalEventState = null
        }

        return continuation
    }

    private fun resumeIfResponseReceived(
        checkpoint: FlowCheckpoint,
        externalEventState: ExternalEventState
    ): FlowContinuation {
        return if (externalEventManager.hasReceivedResponse(externalEventState)) {
            val handler = externalEventFactoryMap.get(externalEventState.factoryClassName)
            val response = externalEventManager.getReceivedResponse(externalEventState, handler.responseType)
            FlowContinuation.Run(handler.resumeWith(checkpoint, response))
        } else {
            FlowContinuation.Continue
        }
    }

    private fun retryOrError(
        config: SmartConfig,
        exception: ExceptionEnvelope,
        externalEventState: ExternalEventState
    ): FlowContinuation {
        val retries = externalEventState.retries
        return if (retries >= config.getLong(FlowConfig.EXTERNAL_EVENT_MAX_RETRIES)) {
            log.error(
                "Retriable exception received from the external event response. Exceeded max retries. Exception: " +
                        exception
            )
            FlowContinuation.Error(CordaRuntimeException(exception.errorMessage))
        } else {
            log.warn(
                "Retriable exception received from the external event response. Retrying exception after delay. " +
                        "Current retry count $retries. Exception: $exception"
            )
            externalEventState.retries = retries.inc()
            FlowContinuation.Continue
        }
    }
}