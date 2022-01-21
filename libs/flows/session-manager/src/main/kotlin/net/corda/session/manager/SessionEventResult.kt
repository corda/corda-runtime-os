package net.corda.session.manager

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState

/**
 * Result from processing a session event via the [SessionManager]
 * [sessionState] is the updated state for this session.
 * [outputSessionEvents] to be sent to the counterparty.
 * This is always an [SessionAck] for a received event or a [SessionError]
 */
data class SessionEventResult(
    val sessionState: SessionState?,
    val outputSessionEvents: List<SessionEvent>?
)
