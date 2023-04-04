package net.corda.flow.mapper.impl.executor

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.flow.mapper.FlowMapperResult
import org.slf4j.LoggerFactory

class SessionErrorExecutor(
    private val eventKey: String,
    private val sessionEvent: SessionEvent,
) {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun processSessionErrorEvents(flowMapperState: FlowMapperState): FlowMapperResult {
        val state = flowMapperState.status
        val errorMsg = "Flow mapper received error event from counterparty for session which does not exist. " +
                "Session may have expired. "

        when (state) {
            FlowMapperStateType.ERROR -> {
                //we get the error and are already in error - log and ignore
                SessionErrorExecutor.log.warn(errorMsg + "Ignoring event. Key: $eventKey, Event: $sessionEvent")
                FlowMapperResult(null, listOf())
            }
            FlowMapperStateType.OPEN -> {
                //we get the error but aren't in error - log and forward error to flow
                SessionErrorExecutor.log.warn(errorMsg + "Forwarding event. Key: $eventKey, Event: $sessionEvent")
                FlowMapperResult(null, listOf())
            }
            FlowMapperStateType.CLOSING -> {
                //log and ignore
                // ** should we ACK this?? should we ACK everything in CLOSING ?
                // ** e.g. in CLOSING ACK everything, in ERROR do not (and tell them in error or ignore?) **
                SessionErrorExecutor.log.warn(errorMsg + "Ignoring event. Key: $eventKey, Event: $sessionEvent")
                FlowMapperResult(null, listOf())
            }
        }
    }
}