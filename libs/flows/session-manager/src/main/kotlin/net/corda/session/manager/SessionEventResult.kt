package net.corda.session.manager

import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.messaging.api.records.Record

/**
 * Result from processing a session event via the [SessionManager]
 * [sessionState] is the updated state for this session.
 * [outputSessionRecord] is an event to be sent to the counterparty.
 * This is always an [SessionAck] for a received event or a [SessionError]
 */
data class SessionEventResult(
    val sessionState: SessionState?,
    val outputSessionRecord: Record<String, FlowMapperEvent>?
)
