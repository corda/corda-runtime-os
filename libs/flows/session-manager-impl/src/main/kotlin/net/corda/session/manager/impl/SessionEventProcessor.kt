package net.corda.session.manager.impl

import net.corda.data.flow.state.session.SessionState

/**
 * Process session events and update the session state for the given events.
 * Outputs the updated session state. Any output messages will be added to [SessionState.sendEventsState]
 */
interface SessionEventProcessor {

    fun execute(): SessionState
}
