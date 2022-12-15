package net.corda.flow.application.sessions

import net.corda.flow.fiber.FlowIORequest
import net.corda.v5.application.messaging.FlowSession

/**
 * Internal APIs for getting and mutating the state of a FlowSession
 */
interface FlowSessionInternal : FlowSession {

    /**
     * Set a session as confirmed.
     */
    fun setSessionConfirmed()

    /**
     * Get the Id of a session
     */
    fun getSessionId(): String

    /**
     * Get the Session info of a session
     */
    fun getSessionInfo(): FlowIORequest.SessionInfo
}
