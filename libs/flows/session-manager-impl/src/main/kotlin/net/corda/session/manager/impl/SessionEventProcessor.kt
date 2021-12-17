package net.corda.session.manager.impl

import net.corda.session.manager.SessionEventResult

/**
 * Process session events and update the session state for the given events.
 * Outputs [SessionEventResult] containing the updated session state and output ack or error record.
 */
interface SessionEventProcessor {

    fun execute(): SessionEventResult
}
