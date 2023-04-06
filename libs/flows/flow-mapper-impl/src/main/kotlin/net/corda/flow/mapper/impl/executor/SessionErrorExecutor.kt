package net.corda.flow.mapper.impl.executor

import net.corda.data.CordaAvroSerializer
import net.corda.data.ExceptionEnvelope
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
import net.corda.schema.Schemas
import org.slf4j.LoggerFactory
import java.time.Instant

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

    private val errorMsg = "Flow mapper received error event from counterparty for session which does not exist. " +
            "Session may have expired. This error event will be %. Key: $eventKey, Event: $sessionEvent"

    override fun execute(): FlowMapperResult {
        return if (flowMapperState == null) {
            log.warn(errorMsg.format("Ignored"))
            FlowMapperResult(null, listOf())
        } else {
            processSessionErrorEvents(flowMapperState)
        }
    }

    private fun processSessionErrorEvents(flowMapperState: FlowMapperState): FlowMapperResult {
        when (flowMapperState.status) {
            FlowMapperStateType.ERROR -> {
                log.warn(errorMsg.format("Ignored"))
                FlowMapperResult(null, listOf())
            }
            FlowMapperStateType.OPEN -> {
                log.warn(errorMsg.format("Forwarded"))
                val sessionId = sessionEvent.sessionId
                FlowMapperResult(
                    flowMapperState, listOf(
                        Record(
                            Schemas.P2P.P2P_OUT_TOPIC, sessionId, appMessageFactory(
                                SessionEvent(
                                    MessageDirection.OUTBOUND,
                                    instant,
                                    sessionEvent.sessionId,
                                    null,
                                    sessionEvent.initiatingIdentity,
                                    sessionEvent.initiatedIdentity,
                                    0,
                                    emptyList(),
                                    SessionError(
                                        ExceptionEnvelope(
                                            "FlowMapper-SessionError",
                                            "Received SessionError with sessionId $sessionId"
                                        )
                                    )
                                ),
                                sessionEventSerializer,
                                flowConfig
                            )
                        )
                    )
                )
                FlowMapperResult(flowMapperState, listOf())
            }
            FlowMapperStateType.CLOSING -> {
                log.warn(errorMsg.format("Ignored"))
                FlowMapperResult(null, listOf())
            }
        }
        log.warn(errorMsg.format("Ignored"))
        return FlowMapperResult(null, listOf())
    }
}