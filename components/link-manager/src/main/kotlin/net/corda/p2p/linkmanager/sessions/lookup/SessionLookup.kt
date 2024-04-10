package net.corda.p2p.linkmanager.sessions.lookup

import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.utils.OutboundMessageContext
import net.corda.p2p.linkmanager.sessions.utils.OutboundMessageState

internal interface SessionLookup : LifecycleWithDominoTile {
    fun <T> getCachedOutboundSessions(
        messagesAndKeys: Map<String?, Collection<OutboundMessageContext<T>>>,
    ): Map<String, Collection<Pair<T, SessionManager.SessionState.SessionEstablished>>>

    fun <T> getAllCachedSessions(
        messagesAndKeys: Map<String, List<T>>,
    ): Map<String, Pair<List<T>, SessionManager.SessionDirection>>

    fun <T> getPersistedOutboundSessions(
        sessionsNotCached: Map<String?, List<OutboundMessageContext<T>>>,
    ): List<OutboundMessageState<T>>

    // session IDs are stored in the metadata for outbound sessions
    fun <T> getPersistedOutboundSessionsBySessionId(
        notInboundSessions: Set<String>,
        sessionsNotCached: Map<String, List<T>>,
    ): List<Pair<List<T>, SessionManager.SessionDirection.Outbound>>

    // inbound and outbound sessions are keyed differently, we will only get inbound
    // when using the session IDs
    fun <T> getPersistedInboundSessions(
        sessionsNotCached: Map<String, List<T>>,
    ): List<Pair<List<T>, SessionManager.SessionDirection.Inbound>>
}