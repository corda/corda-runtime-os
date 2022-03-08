package net.corda.flow.mapper.impl.executor

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.v5.base.util.contextLogger
import java.time.Instant

class SessionEventExecutor(
    private val eventKey: String,
    private val sessionEvent: SessionEvent,
    private val flowMapperState: FlowMapperState?,
    private val instant: Instant,
) : FlowMapperEventExecutor {

    private companion object {
        private val log = contextLogger()
    }

    private val messageDirection = sessionEvent.messageDirection
    private val outputTopic = getSessionEventOutputTopic(messageDirection)

    override fun execute(): FlowMapperResult {
        return if (flowMapperState == null) {
            handleNullState()
        } else {
            processOtherSessionEvents(flowMapperState)
        }
    }

    private fun handleNullState(): FlowMapperResult {
        log.error("Flow mapper processed session event for expired closed session. Key: $eventKey, Event: $sessionEvent")
        val counterpartySessionId = toggleSessionId(sessionEvent.sessionId)
        val record = Record(
            Schemas.P2P.P2P_OUT_TOPIC, counterpartySessionId, FlowMapperEvent(
                SessionEvent(MessageDirection.OUTBOUND, instant, counterpartySessionId, null,
                    SessionError(ExceptionEnvelope(
                            "FlowMapper-SessionExpired",
                            "Tried to process session event for expired session with sessionId $counterpartySessionId"
                        )
                    )
                )
            )
        )
        return FlowMapperResult(null, listOf(record))
    }

    /**
     * Toggles the sessionId for the event and output the session event to the correct topic and key
     */
    private fun processOtherSessionEvents(flowMapperState: FlowMapperState): FlowMapperResult {
        val outputRecord = if (messageDirection == MessageDirection.OUTBOUND) {
            val sessionId = toggleSessionId(eventKey)
            sessionEvent.sessionId = sessionId
            Record(outputTopic, sessionId, FlowMapperEvent(sessionEvent))
        } else {
            Record(outputTopic, flowMapperState.flowKey, FlowEvent(flowMapperState.flowKey, sessionEvent))
        }

        return FlowMapperResult(flowMapperState, listOf(outputRecord))
    }
}
