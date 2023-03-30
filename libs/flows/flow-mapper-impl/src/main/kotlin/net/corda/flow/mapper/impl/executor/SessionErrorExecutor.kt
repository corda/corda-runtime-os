package net.corda.flow.mapper.impl.executor

import net.corda.data.CordaAvroSerializer
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.data.p2p.app.AppMessage
import net.corda.flow.mapper.FlowMapperResult
import net.corda.libs.configuration.SmartConfig
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
    fun processSessionErrorEvents(flowMapperState: FlowMapperState): FlowMapperResult {
        val state = flowMapperState.status
        val event = sessionEvent.payload

        if(state == FlowMapperStateType.ERROR && event is SessionError) {
            //we get the error and are already in error - do nothing
        } else if (state == FlowMapperStateType.CLOSING) {
            //ignore
        } else if (state == FlowMapperStateType.OPEN) {
            //forward error to flow
        }
    }
}