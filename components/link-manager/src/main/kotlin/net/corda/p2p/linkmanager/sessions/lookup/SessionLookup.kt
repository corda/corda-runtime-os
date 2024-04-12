package net.corda.p2p.linkmanager.sessions.lookup

import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.utils.OutboundMessageContext
import net.corda.p2p.linkmanager.sessions.utils.OutboundMessageState

internal interface SessionLookup : LifecycleWithDominoTile {
    /**
     * Retrieves the cached outbound sessions from the [SessionCache] based on the outbound session keys,
     * that contains the counterparty hash.
     *
     * @param keysAndMessages Map of the counterparty hashes which are used for keying outbound sessions and the message
     * contexts.
     *
     * @return Session keys mapped to the trace and session information.
     */
    fun <T> getCachedOutboundSessions(
        keysAndMessages: Map<String?, Collection<OutboundMessageContext<T>>>,
    ): Map<String, Collection<Pair<T, SessionManager.SessionState.SessionEstablished>>>

    /**
     * Retrieves all cached sessions from the [SessionCache], both inbound and outbound.
     *
     * @param messagesAndKeys Map of session messages and keys.
     *
     * @return Session keys mapped to the messages and session direction.
     */
    fun <T> getAllCachedSessions(
        messagesAndKeys: Map<String, List<T>>,
    ): Map<String, Pair<List<T>, SessionManager.SessionDirection>>

    /**
     * Retrieves persisted outbound sessions from the [StateManager] database. Should be used for sessions which are
     * not cached.
     *
     * @param keysAndMessages Map of the counterparty hashes which are used for keying outbound sessions and the message
     * contexts.
     *
     * @return List of outbound message states.
     */
    fun <T> getPersistedOutboundSessions(
        keysAndMessages: Map<String?, List<OutboundMessageContext<T>>>,
    ): List<OutboundMessageState<T>>

    /**
     * Outbound sessions are keyed by counterparty information.
     * Session IDs are stored in the metadata for outbound sessions. This function retrieves persisted outbound
     * sessions from the [StateManager] database. Should be used for sessions which are not cached.
     * Writes the session to the [SessionCache].
     *
     * @param sessionIds The session IDs for outbound sessions.
     * @param sessionIdsAndMessages Map of session messages and IDs.
     *
     * @return Messages mapped to session direction.
     */
    fun <T> getPersistedOutboundSessionsBySessionId(
        sessionIds: Set<String>,
        sessionIdsAndMessages: Map<String, List<T>>,
    ): List<Pair<List<T>, SessionManager.SessionDirection.Outbound>>

    /**
     * Inbound sessions are keyed by session IDs.
     * This function retrieves persisted inbound sessions from the [StateManager] database.
     * Should be used for sessions which are not cached.
     * Writes the session to the [SessionCache].
     *
     * @param sessionIdsAndMessages Map of session messages and IDs.
     *
     * @return Messages mapped to session direction.
     */
    fun <T> getPersistedInboundSessions(
        sessionIdsAndMessages: Map<String, List<T>>,
    ): List<Pair<List<T>, SessionManager.SessionDirection.Inbound>>
}