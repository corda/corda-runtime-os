package net.corda.flow.mapper.impl.executor

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger

class SessionEventExecutor(
    private val eventKey: String,
    private val sessionEvent: SessionEvent,
    private val flowMapperState: FlowMapperState?,
) : FlowMapperEventExecutor {

    private companion object {
        private val log = contextLogger()
    }

    private val messageDirection = sessionEvent.messageDirection
    private val outputTopic = getSessionEventOutputTopic(messageDirection)

    override fun execute(): FlowMapperResult {
        return if (flowMapperState == null) {
            //expired closed session. This is likely a bug and we should return an error event to the sender - CORE-3207
            log.error("Event received for expired closed session. Key: $eventKey, Event: $sessionEvent")
            FlowMapperResult(flowMapperState, emptyList())
        } else {
            processOtherSessionEvents(flowMapperState)
        }
    }

    /**
     * Toggles the sessionId for the event and output the session event to the correct topic and key
     */
    private fun processOtherSessionEvents(flowMapperState: FlowMapperState): FlowMapperResult {
        val sessionId = toggleSessionId(eventKey)
        sessionEvent.sessionId = sessionId

        val outputRecord = if (messageDirection == MessageDirection.OUTBOUND) {
            Record(outputTopic, sessionId, FlowMapperEvent(sessionEvent))
        } else {
            Record(outputTopic, flowMapperState.flowKey, FlowEvent(flowMapperState.flowKey, sessionEvent))
        }

        return FlowMapperResult(flowMapperState, listOf(outputRecord))
    }
}
