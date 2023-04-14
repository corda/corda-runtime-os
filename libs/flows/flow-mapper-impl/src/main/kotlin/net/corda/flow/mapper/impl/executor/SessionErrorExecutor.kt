package net.corda.flow.mapper.impl.executor

import net.corda.data.CordaAvroSerializer
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
import java.time.Instant

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
    }

    private val messageDirection = sessionEvent.messageDirection
    private val outputTopic = getSessionEventOutputTopic(messageDirection)

    private val errorMsg = "Flow mapper received error event from counterparty for session which does not exist. " +
            "Session may have expired. Key: $eventKey, Event: $sessionEvent. "

    override fun execute(): FlowMapperResult {
        return if (flowMapperState == null) {
            log.warn(errorMsg + "Ignoring event.")
            FlowMapperResult(flowMapperState, listOf())
        } else {
            processSessionErrorEvents(flowMapperState)
        }
    }

    private fun processSessionErrorEvents(flowMapperState: FlowMapperState): FlowMapperResult {
        val sessionId = sessionEvent.sessionId
        return when (flowMapperState.status) {
            null -> {
                log.warn("FlowMapperState with null status. Key: $eventKey, Event: $sessionEvent.")
                FlowMapperResult(null, listOf())
            }
            FlowMapperStateType.ERROR -> {
                log.warn(errorMsg + "Ignoring event.")
                FlowMapperResult(flowMapperState, listOf())
            }
            FlowMapperStateType.OPEN -> {
                log.warn(errorMsg + "Forwarding event.")
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
                log.warn(errorMsg + "Ignoring event.")
                flowMapperState.status = FlowMapperStateType.ERROR
                FlowMapperResult(flowMapperState, listOf())
            }
        }
    }
}