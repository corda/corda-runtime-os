package net.corda.flow.mapper.impl.executor

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.messaging.api.records.Record
import net.corda.session.manager.Constants
import org.slf4j.LoggerFactory
import java.time.Instant

@Suppress("LongParameterList")
class SessionEventExecutor(
    private val eventKey: String,
    private val sessionEvent: SessionEvent,
    private val flowMapperState: FlowMapperState?,
    private val instant: Instant,
) : FlowMapperEventExecutor {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val messageDirection = sessionEvent.messageDirection
    private val outputTopic = getSessionEventOutputTopic(messageDirection)

    override fun execute(): FlowMapperResult {
        return if (flowMapperState == null) {
            handleNullState()
        } else {
            processOtherSessionEvents(flowMapperState, instant)
        }
    }

    private fun handleNullState(): FlowMapperResult {
        val eventPayload = sessionEvent.payload

        return if (eventPayload !is SessionError) {
            log.warn(
                "Flow mapper received session event for session which does not exist. Session may have expired. Returning error to " +
                        "counterparty. Key: $eventKey, Event: class ${sessionEvent.payload::class.java}, $sessionEvent"
            )
            val sessionId = sessionEvent.sessionId

            val errEvent = SessionEvent(
                MessageDirection.INBOUND,
                instant,
                toggleSessionId(sessionEvent.sessionId),
                null,
                sessionEvent.initiatingIdentity,
                sessionEvent.initiatedIdentity,
                0,
                emptyList(),
                SessionError(
                    ExceptionEnvelope(
                        "FlowMapper-SessionExpired",
                        "Tried to process session event for expired session with sessionId $sessionId"
                    )
                )
            )

            FlowMapperResult(
                null, listOf(
                    Record(outputTopic, errEvent.sessionId, FlowMapperEvent(errEvent))
                )
            )
        } else {
            log.warn(
                "Flow mapper received error event from counterparty for session which does not exist. Session may have expired. " +
                        "Ignoring event. Key: $eventKey, Event: $sessionEvent"
            )
            FlowMapperResult(null, listOf())
        }
    }

    /**
     * Output the session event to the correct topic and key
     */
    private fun processOtherSessionEvents(flowMapperState: FlowMapperState, instant: Instant): FlowMapperResult {
        val errorMsg = "Flow mapper received error event from counterparty for session which does not exist. " +
                "Session may have expired. Key: $eventKey, Event: $sessionEvent. "

        return when (flowMapperState.status) {
            null -> {
                log.warn("FlowMapperState with null status. Key: $eventKey, Event: $sessionEvent.")
                FlowMapperResult(null, listOf())
            }
            FlowMapperStateType.CLOSING -> {
                if (messageDirection == MessageDirection.OUTBOUND) {
                    log.warn("Attempted to send a message but flow mapper state is in CLOSING. Session ID: ${sessionEvent.sessionId}")
                    FlowMapperResult(flowMapperState, listOf())
                } else {
                    // Why were we sending to P2P is direction was INBOUND?
                    if (sessionEvent.payload is SessionClose) {
                        val ackEvent = SessionEvent(
                            MessageDirection.INBOUND,
                            instant,
                            toggleSessionId(sessionEvent.sessionId),
                            null,
                            sessionEvent.initiatingIdentity,
                            sessionEvent.initiatedIdentity,
                            sessionEvent.sequenceNum,
                            emptyList(),
                            SessionAck()
                        )
                        val outputRecord =
                            Record(outputTopic, ackEvent.sessionId, FlowMapperEvent(ackEvent))
                        FlowMapperResult(flowMapperState, listOf(outputRecord))
                    } else {
                        FlowMapperResult(flowMapperState, listOf())
                    }
                }
            }
            FlowMapperStateType.OPEN -> {
                val outputRecord = if (messageDirection == MessageDirection.OUTBOUND) {
                    sessionEvent.messageDirection = MessageDirection.INBOUND
                    sessionEvent.sessionId = toggleSessionId(sessionEvent.sessionId)
                    Record(
                        outputTopic,
                        sessionEvent.sessionId,
                        FlowMapperEvent(sessionEvent)
                    )
                } else {
                    Record(outputTopic, flowMapperState.flowId, FlowEvent(flowMapperState.flowId, sessionEvent))
                }
                FlowMapperResult(flowMapperState, listOf(outputRecord))
            }
            FlowMapperStateType.ERROR -> {
                log.warn(errorMsg + "Ignoring event.")
                FlowMapperResult(flowMapperState, listOf())
            }
        }
    }

    /**
     * Toggle the [sessionId] to that of the other party and return it.
     * Initiating party sessionId will be a random UUID.
     * Initiated party sessionId will be the initiating party session id with a suffix of "-INITIATED" added.
     * @return the toggled session id
     */
    private fun toggleSessionId(sessionId: String): String {
        return if (sessionId.endsWith(Constants.INITIATED_SESSION_ID_SUFFIX)) {
            sessionId.removeSuffix(Constants.INITIATED_SESSION_ID_SUFFIX)
        } else {
            "$sessionId${Constants.INITIATED_SESSION_ID_SUFFIX}"
        }
    }
}
