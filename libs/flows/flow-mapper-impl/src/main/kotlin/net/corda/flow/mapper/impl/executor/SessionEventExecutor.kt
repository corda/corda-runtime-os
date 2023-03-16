package net.corda.flow.mapper.impl.executor

import net.corda.data.CordaAvroSerializer
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.p2p.app.AppMessage
import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.interop.FacadeInvocation
import net.corda.messaging.interop.FacadeInvocationResult
import net.corda.schema.Schemas
import org.slf4j.LoggerFactory
import java.time.Instant

@Suppress("LongParameterList")
class SessionEventExecutor(
    private val eventKey: String,
    private val sessionEvent: SessionEvent,
    private val flowMapperState: FlowMapperState?,
    private val instant: Instant,
    private val sessionEventSerializer: CordaAvroSerializer<SessionEvent>,
    private val appMessageFactory: (SessionEvent, CordaAvroSerializer<SessionEvent>, SmartConfig) -> AppMessage,
    private val flowConfig: SmartConfig
) : FlowMapperEventExecutor {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val messageDirection = sessionEvent.messageDirection
    private val outputTopic = getSessionEventOutputTopic(messageDirection)

    override fun execute(): FlowMapperResult {
        return if (flowMapperState == null) {
            handleNullState()
        } else if (sessionEvent.isInteropEvent()) {
            processOtherSessionEventsInterop(flowMapperState)
        } else {
            processOtherSessionEvents(flowMapperState)
        }
    }

    private fun handleNullState(): FlowMapperResult {
        val eventPayload = sessionEvent.payload

        return if (eventPayload !is SessionError) {
            log.error("Flow mapper received session event for session which does not exist. Session may have expired. Returning error to " +
                    "counterparty. Key: $eventKey, Event: $sessionEvent")
            val sessionId = sessionEvent.sessionId
            FlowMapperResult(
                null, listOf(
                    Record(
                        Schemas.P2P.P2P_OUT_TOPIC, sessionId, appMessageFactory(
                            SessionEvent(
                                MessageDirection.OUTBOUND, instant, sessionEvent.sessionId, null, sessionEvent.initiatingIdentity,
                                sessionEvent.initiatedIdentity, 0, emptyList(),
                                SessionError(
                                    ExceptionEnvelope(
                                        "FlowMapper-SessionExpired",
                                        "Tried to process session event for expired session with sessionId $sessionId"
                                    )
                                )
                            ),
                            sessionEventSerializer,
                            flowConfig
                        )
                    )
                )
            )
        } else {
            log.warn("Flow mapper received error event from counterparty for session which does not exist. Session may have expired. " +
                    "Ignoring event. Key: $eventKey, Event: $sessionEvent")
            FlowMapperResult(null, listOf())
        }
    }

    /**
     * Output the session event to the correct topic and key
     */
    private fun processOtherSessionEvents(flowMapperState: FlowMapperState): FlowMapperResult {
        val outputRecord = if (messageDirection == MessageDirection.OUTBOUND) {
            Record(outputTopic, sessionEvent.sessionId, appMessageFactory(sessionEvent, sessionEventSerializer, flowConfig))
        } else {
            Record(outputTopic, flowMapperState.flowId, FlowEvent(flowMapperState.flowId, sessionEvent))
        }

        return FlowMapperResult(flowMapperState, listOf(outputRecord))
    }

    private fun processOtherSessionEventsInterop(flowMapperState: FlowMapperState): FlowMapperResult {
        return if (messageDirection == MessageDirection.OUTBOUND) {
            val facadeInvocation = sessionEvent.payload as FacadeInvocation
            val returnEvent = SessionEvent(
                MessageDirection.INBOUND,
                Instant.now(),
                sessionEvent.sessionId,
                1,
                sessionEvent.initiatingIdentity,
                sessionEvent.initiatedIdentity,
                sessionEvent.receivedSequenceNum,
                emptyList(),
                FacadeInvocationResult(facadeInvocation.payload)
            )
            val record = Record(
                Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC,
                sessionEvent.sessionId,
                appMessageFactory(returnEvent, sessionEventSerializer, flowConfig)
            )
            FlowMapperResult(flowMapperState, listOf(record))
        } else {
            // CORE-10240, handle inbound events from the interop processor properly
            log.warn("Flow mapper received inbound interop event from counterparty. This should not happen yet! " +
                    "Ignoring event. Key: $eventKey, Event: $sessionEvent")
            FlowMapperResult(flowMapperState, listOf())
        }
    }
}
