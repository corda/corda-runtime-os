package net.corda.flow.mapper.impl.executor

import java.time.Instant
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.data.p2p.app.AppMessage
import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
class SessionErrorExecutor(
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

        private const val IGNORING = "ignoring"
        private const val FORWARDING = "forwarding"
    }

    private val messageDirection = sessionEvent.messageDirection
    private val outputTopic = getSessionEventOutputTopic(messageDirection)

    private val defaultMsg = "$messageDirection flow mapper received error event while in $flowMapperState, {} event. " +
            "Key: $eventKey, Event: $sessionEvent"
    private val missingSessionMsg = "$messageDirection flow mapper received error event from counterparty for session " +
            "which does not exist. Session may have expired. Key: $eventKey, Event: $sessionEvent. "

    val sessionId = sessionEvent.sessionId

    val errEvent = SessionEvent(
        MessageDirection.INBOUND,
        instant,
        toggleSessionId(sessionEvent.sessionId),
        null,
        sessionEvent.initiatingIdentity,
        sessionEvent.initiatedIdentity,
        sessionEvent.receivedSequenceNum,
        emptyList(),
        SessionError(
            ExceptionEnvelope(
                "FlowMapper-SessionError",
                "Received SessionError with sessionId $sessionId"
            )
        )
    )

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
                if (messageDirection == MessageDirection.OUTBOUND) {
                    FlowMapperResult(
                        flowMapperState, listOf(
                            createP2PRecord(
                                sessionEvent,
                                SessionError(
                                    ExceptionEnvelope(
                                        "FlowMapper-SessionError",
                                        "Received SessionError with sessionId $sessionId"
                                    )
                                ),
                                instant,
                                sessionEventSerializer,
                                appMessageFactory,
                                flowConfig,
                                sessionEvent.receivedSequenceNum
                            )
                        )
                    )
                } else {
                    val outputRecord = Record(outputTopic, flowMapperState.flowId, FlowEvent(flowMapperState.flowId, sessionEvent))
                    FlowMapperResult(flowMapperState, listOf(outputRecord))
                }
            }
            FlowMapperStateType.CLOSING -> {
                log.debug(defaultMsg.format(IGNORING))
                flowMapperState.status = FlowMapperStateType.ERROR
                FlowMapperResult(flowMapperState, listOf())
            }
        }
    }
}