package net.corda.p2p.linkmanager.sessions.lookup

import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.utils.InboundSessionMessageContext
import net.corda.p2p.linkmanager.sessions.utils.OutboundMessageContext
import net.corda.p2p.linkmanager.sessions.utils.OutboundMessageState

internal interface SessionLookup {
    fun <T> getCachedOutboundSessions(
        messagesAndKeys: Map<String?, Collection<OutboundMessageContext<T>>>,
    ): Map<String, Collection<Pair<T, SessionManager.SessionState.SessionEstablished>>>

    fun <T> getAllCachedSessions(
        messagesAndKeys: Map<String, List<T>>,
    ): Map<String, Pair<List<T>, SessionManager.SessionDirection>>

    fun <T> getPersistedOutboundSessions(
        sessionsNotCached: Map<String?, List<OutboundMessageContext<T>>>,
    ): List<OutboundMessageState<T>>

    fun <T> getPersistedOutboundSessionsBySessionId(
        notInboundSessions: Set<String>,
        sessionsNotCached: Map<String, List<T>>,
    ): List<Pair<List<T>, SessionManager.SessionDirection.Outbound>>

    fun <T> getPersistedInboundSessions(
        sessionsNotCached: Map<String, List<T>>,
    ): List<Pair<List<T>, SessionManager.SessionDirection.Inbound>>

    fun <T> getSessionIdIfInboundSessionMessage(data: Any, trace: T): InboundSessionMessageContext<T>?

    /**
     * Gets the states for the outbound session based on the messages and their IDs.
     */
    //fun <T> getOutboundSessionStates(keysAndMessages: Map<String?, List<OutboundMessageContext<T>>>): List<OutboundMessageState<T>>

    //fun getOutboundSessions(keysAndMessages: Map<String?): List<SessionManager.SessionDirection?>
}