package net.corda.p2p.linkmanager.sessions.lookup

import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.StatefulSessionManagerImpl

internal interface SessionLookup {
    /**
     * Looks for a given session with [sessionID] first in the session cache and then in the
     * state manager DB, if not found in the cache.
     *
     * @param sessionID The ID of the session.
     *
     * @return The session itself, or null if no session was found with given [sessionID].
     */
    fun getSessionBySessionId(sessionID: String): SessionManager.SessionDirection?

    /**
     * Get sthe outbound session based
     */
    fun <T> getOutboundSessions(keysAndMessages: Map<String?, List<StatefulSessionManagerImpl.OutboundMessageContext<T>>>)
}