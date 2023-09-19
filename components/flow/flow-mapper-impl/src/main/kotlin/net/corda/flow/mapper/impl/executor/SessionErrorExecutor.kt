package net.corda.flow.mapper.impl.executor

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.flow.mapper.factory.RecordFactory
import net.corda.libs.configuration.SmartConfig
import org.slf4j.LoggerFactory
import java.time.Instant

@Suppress("LongParameterList")
class SessionErrorExecutor(
    private val eventKey: String,
    private val sessionEvent: SessionEvent,
    private val flowMapperState: FlowMapperState?,
    private val flowConfig: SmartConfig,
    private val recordFactory: RecordFactory,
    private val instant: Instant
) : FlowMapperEventExecutor {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private const val IGNORING = "ignoring"
        private const val FORWARDING = "forwarding"
    }

    private val messageDirection = sessionEvent.messageDirection
    private val defaultMsg = "$messageDirection flow mapper received error event while in $flowMapperState, {} event. " +
            "Key: $eventKey, Event: $sessionEvent"
    private val missingSessionMsg = "$messageDirection flow mapper received error event from counterparty for session " +
            "which does not exist. Session may have expired. Key: $eventKey, Event: $sessionEvent. "

    override fun execute(): FlowMapperResult {
        return if (flowMapperState == null) {
            log.warn(missingSessionMsg + "Ignoring event.")
            FlowMapperResult(flowMapperState, listOf())
        } else {
            processSessionErrorEvents(flowMapperState)
        }
    }

    private fun processSessionErrorEvents(flowMapperState: FlowMapperState): FlowMapperResult {
        return when (flowMapperState.status) {
            null -> {
                log.debug("FlowMapperState with null status. Key: {}, Event: {}.", eventKey, sessionEvent)
                FlowMapperResult(null, listOf())
            }
            FlowMapperStateType.ERROR -> {
                log.debug(defaultMsg.format(IGNORING))
                FlowMapperResult(flowMapperState, listOf())
            }
            FlowMapperStateType.OPEN -> {
                log.debug(defaultMsg.format(FORWARDING))
                flowMapperState.status = FlowMapperStateType.ERROR
                val payload = if (sessionEvent.messageDirection == MessageDirection.OUTBOUND) {
                    ExceptionEnvelope(
                        "FlowMapper-SessionError",
                        "Received SessionError with sessionId ${sessionEvent.sessionId}"
                    )
                } else {
                    (sessionEvent.payload as? SessionError)?.errorMessage ?: ExceptionEnvelope(
                        "FlowMapper-SessionError",
                        "Received SessionError with sessionId ${sessionEvent.sessionId}"
                    )
                }
                val record = recordFactory.forwardError(
                    sessionEvent,
                    payload,
                    instant,
                    flowConfig,
                    flowMapperState.flowId
                )
                FlowMapperResult(flowMapperState, listOf(record))
            }
            FlowMapperStateType.CLOSING -> {
                log.debug(defaultMsg.format(IGNORING))
                flowMapperState.status = FlowMapperStateType.ERROR
                FlowMapperResult(flowMapperState, listOf())
            }
        }
    }
}