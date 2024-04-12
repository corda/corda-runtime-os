package net.corda.p2p.linkmanager.sessions.lookup

import net.corda.data.p2p.event.SessionDirection
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.membership.getSessionCounterpartiesFromMessage
import net.corda.p2p.linkmanager.sessions.SessionCache
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.SessionEstablished
import net.corda.p2p.linkmanager.sessions.StateManagerWrapper
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata.Companion.toOutbound
import net.corda.p2p.linkmanager.sessions.utils.OutboundMessageContext
import net.corda.p2p.linkmanager.sessions.utils.OutboundMessageState
import net.corda.p2p.linkmanager.sessions.writer.SessionWriter

internal class SessionLookupImpl(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val sessionCache: SessionCache,
    private val sessionWriter: SessionWriter,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val stateManager: StateManagerWrapper,
) : SessionLookup {
    override fun <T> getCachedOutboundSessions(
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

    override fun <T> getAllCachedSessions(
        messagesAndKeys: Map<String, List<T>>,
    ): Map<String, Pair<List<T>, SessionManager.SessionDirection>> {
        return messagesAndKeys.mapNotNull { (key, keyAndMessage) ->
            sessionCache.getBySessionIfCached(key)?.let { sessionDirection ->
                key to (keyAndMessage to sessionDirection)
            }
        }.toMap()
    }

    override fun <T> getPersistedOutboundSessions(
        keysAndMessages: Map<String?, List<OutboundMessageContext<T>>>,
    ): List<OutboundMessageState<T>> {
        return stateManager.get(keysAndMessages.keys.filterNotNull())
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

    override fun <T> getPersistedOutboundSessionsBySessionId(
        sessionIds: Set<String>,
        sessionIdsAndMessages: Map<String, List<T>>,
    ): List<Pair<List<T>, SessionManager.SessionDirection.Outbound>> {
        return stateManager.findStatesMatchingAny(
            sessionIds.map { getSessionIdFilter(it) },
        )
            .entries
            .mapNotNull { (key, state) ->
                val session = state.sessionState.sessionData as? Session ?: return@mapNotNull null
                val sessionId = state.managerState.metadata.toOutbound().sessionId
                sessionIdsAndMessages[sessionId]?.let { traceables ->
                    val outboundSession = sessionWriter.cacheSession(
                        SessionDirection.OUTBOUND,
                        state.toCounterparties(),
                        session,
                        key,
                    ) as SessionManager.SessionDirection.Outbound
                    traceables to outboundSession
                }
            }
    }

    private fun getSessionIdFilter(sessionId: String): MetadataFilter = MetadataFilter("sessionId", Operation.Equals, sessionId)

    override fun <T> getPersistedInboundSessions(
        sessionIdsAndMessages: Map<String, List<T>>,
    ): List<Pair<List<T>, SessionManager.SessionDirection.Inbound>> {
        return stateManager.get(sessionIdsAndMessages.keys)
            .entries
            .mapNotNull { (sessionId, state) ->
                val session = state.sessionState.sessionData as? Session ?: return@mapNotNull null
                sessionIdsAndMessages[sessionId]?.let { traceables ->
                    val inboundSession = sessionWriter.cacheSession(
                        SessionDirection.INBOUND,
                        state.toCounterparties(),
                        session,
                    ) as SessionManager.SessionDirection.Inbound
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