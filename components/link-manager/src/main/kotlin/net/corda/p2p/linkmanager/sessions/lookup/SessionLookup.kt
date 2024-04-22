package net.corda.p2p.linkmanager.sessions.lookup

import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.p2p.crypto.protocol.api.CheckRevocation
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.common.CommonComponents
import net.corda.p2p.linkmanager.membership.getSessionCounterpartiesFromMessage
import net.corda.p2p.linkmanager.sessions.ReEstablishmentMessageSender
import net.corda.p2p.linkmanager.sessions.SessionCache
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.SessionEstablished
import net.corda.p2p.linkmanager.sessions.expiration.SessionExpirationScheduler
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata.Companion.isOutbound
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata.Companion.toOutbound
import net.corda.p2p.linkmanager.sessions.metadata.toCounterparties
import net.corda.p2p.linkmanager.sessions.utils.OutboundMessageContext
import net.corda.p2p.linkmanager.sessions.utils.OutboundMessageState
import net.corda.p2p.linkmanager.sessions.writer.SessionWriter
import net.corda.p2p.linkmanager.state.SessionState

@Suppress("LongParameterList")
internal class SessionLookup(
    private val commonComponents: CommonComponents,
    private val sessionCache: SessionCache,
    private val sessionWriter: SessionWriter,
    private val sessionExpirationScheduler: SessionExpirationScheduler,
    private val checkRevocation: CheckRevocation,
    private val reEstablishmentMessageSender: ReEstablishmentMessageSender,
) : LifecycleWithDominoTile {
    private val coordinatorFactory
        get() = commonComponents.lifecycleCoordinatorFactory
    private val membershipGroupReaderProvider
        get() = commonComponents.membershipGroupReaderProvider
    private val stateConvertor
        get() = commonComponents.stateConvertor
    private val stateManager
        get() = commonComponents.stateManager

    val name: LifecycleCoordinatorName = stateManager.name

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
    ): Map<String, Collection<Pair<T, SessionEstablished>>> {
        val allCached = sessionCache.getAllPresentOutboundSessions(keysAndMessages.keys.filterNotNull())
        return allCached.mapValues { entry ->
            val contexts = keysAndMessages[entry.key]
            val message = contexts?.firstOrNull()
                ?.message
                ?.message ?: return@mapValues emptyList()
            val counterparties = membershipGroupReaderProvider.getSessionCounterpartiesFromMessage(message)
                ?: return@mapValues emptyList()

            contexts.map { context ->
                context.trace to SessionEstablished(entry.value.session, counterparties)
            }
        }.toMap()
    }

    /**
     * Retrieves all cached sessions from the [SessionCache], both inbound and outbound.
     *
     * @param messagesAndKeys Map of session messages and keys.
     *
     * @return Session keys mapped to the messages and session direction.
     */
    fun <T> getAllCachedSessions(
        messagesAndKeys: Map<String, List<T>>,
    ): Map<String, Pair<List<T>, SessionManager.SessionDirection>> {
        return messagesAndKeys.mapNotNull { (key, keyAndMessage) ->
            sessionCache.getBySessionIfCached(key)?.let { sessionDirection ->
                key to (keyAndMessage to sessionDirection)
            }
        }.toMap()
    }

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
    ): List<OutboundMessageState<T>> {
        return findStates(keysAndMessages.keys.filterNotNull())
            .let { states ->
                keysAndMessages.map { (id, items) ->
                    OutboundMessageState(
                        id,
                        states[id],
                        items,
                    )
                }
            }
    }

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
    ): List<Pair<List<T>, SessionManager.SessionDirection.Outbound>> {
        return findStatesMatchingAny(
            sessionIds.map { getSessionIdFilter(it) },
        )
            .entries
            .mapNotNull { (key, state) ->
                val session = state.sessionState.sessionData as? Session ?: return@mapNotNull null
                val sessionId = state.managerState.metadata.toOutbound().sessionId
                sessionIdsAndMessages[sessionId]?.let { traceables ->
                    val outboundSession = sessionWriter.cacheOutboundSession(
                        key,
                        state.toCounterparties(),
                        session,
                    )
                    traceables to outboundSession
                }
            }
    }

    fun findStates(
        keys: Collection<String>,
    ) = sessionExpirationScheduler.validateStatesAndScheduleExpiry(
        stateManager.get(keys),
    ).toStates()

    fun findStatesMatchingAny(
        filters: Collection<MetadataFilter>,
    ) = sessionExpirationScheduler.validateStatesAndScheduleExpiry(
        stateManager.findByMetadataMatchingAny(filters),
    ).toStates()

    private fun Map<String, State>.toStates():  Map<String, StateManagerSessionState> {
        return this.mapNotNull { (key, state) ->
            val session = stateConvertor.toCordaSessionState(
                state,
                checkRevocation,
            )
            if (session == null) {
                sessionCache.forgetState(state)
                if (!state.metadata.isOutbound()) {
                    reEstablishmentMessageSender.send(state)
                }
                null
            } else {
                key to StateManagerSessionState(state, session)
            }
        }.toMap()
    }

    data class StateManagerSessionState(
        val managerState: State,
        val sessionState: SessionState,
    ) {
        fun toCounterparties() = managerState.toCounterparties()
    }

    /**
     * Generates the session ID query filter for querying outbound sessions from the [StateManager] database when
     * using session IDs instead of keys.
     *
     * @param sessionId The ID of the session.
     *
     * @return The filter that should be used when querying data.
     */
    fun getSessionIdFilter(sessionId: String): MetadataFilter = MetadataFilter("sessionId", Operation.Equals, sessionId)

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
    ): List<Pair<List<T>, SessionManager.SessionDirection.Inbound>> {
        return findStates(sessionIdsAndMessages.keys)
            .entries
            .mapNotNull { (sessionId, state) ->
                val session = state.sessionState.sessionData as? Session ?: return@mapNotNull null
                sessionIdsAndMessages[sessionId]?.let { traceables ->
                    val inboundSession = sessionWriter.cacheInboundSession(
                        state.toCounterparties(),
                        session,
                    )
                    traceables to inboundSession
                }
            }
    }

    override val dominoTile: DominoTile =
        ComplexDominoTile(
            this::class.java.simpleName,
            coordinatorFactory,
            dependentChildren =
            setOf(
                stateManager.name,
            ),
            managedChildren = emptySet(),
        )
}