package net.corda.flow.mapper.impl.executor

import net.corda.data.CordaAvroSerializer
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.data.p2p.app.AppMessage
import net.corda.flow.mapper.FlowMapperResult
import net.corda.libs.configuration.SmartConfig
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
) {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun processSessionErrorEvents(flowMapperState: FlowMapperState): FlowMapperResult {
        val state = flowMapperState.status
        val event = sessionEvent.payload

        //**When statement here instead
        //**constant for log msgs

        when (state) {
            FlowMapperStateType.ERROR -> {
                //we get the error and are already in error - log and ignore
                SessionErrorExecutor.log.warn("Flow mapper received error event from counterparty for session which does not exist. Session may have expired. " +
                        "Ignoring event. Key: $eventKey, Event: $sessionEvent")
                FlowMapperResult(null, listOf())
            }
            FlowMapperStateType.OPEN -> {
                //we get the error but aren't in error - log and forward error to flow
                SessionErrorExecutor.log.warn("Flow mapper received error event from counterparty for session which does not exist. Session may have expired. " +
                        "Ignoring event. Key: $eventKey, Event: $sessionEvent")
                FlowMapperResult(null, listOf())
            }
            FlowMapperStateType.CLOSING -> {
                //log and ignore
                // ** should we ACK this?? should we ACK everything in CLOSING ?
                // ** e.g. in CLOSING ACK everything, in ERROR do not (and tell them in error or ignore?) **
                SessionErrorExecutor.log.warn("Flow mapper received error event from counterparty for session which does not exist. Session may have expired. " +
                        "Ignoring event. Key: $eventKey, Event: $sessionEvent")
                FlowMapperResult(null, listOf())
            }
        }

    }
}