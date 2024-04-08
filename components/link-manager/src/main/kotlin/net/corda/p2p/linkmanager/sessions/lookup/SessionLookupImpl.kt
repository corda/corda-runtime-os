package net.corda.p2p.linkmanager.sessions.lookup

import net.corda.data.p2p.event.SessionDirection
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.StateManager
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.p2p.crypto.protocol.api.CheckRevocation
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.membership.getSessionCounterpartiesFromMessage
import net.corda.p2p.linkmanager.sessions.ReEstablishmentMessageSender
import net.corda.p2p.linkmanager.sessions.SessionCache
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.SessionEstablished
import net.corda.p2p.linkmanager.sessions.StateConvertor
import net.corda.p2p.linkmanager.sessions.StateManagerWrapper
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata.Companion.toOutbound
import net.corda.p2p.linkmanager.sessions.utils.InboundSessionMessageContext
import net.corda.p2p.linkmanager.sessions.utils.OutboundMessageContext
import net.corda.p2p.linkmanager.sessions.utils.OutboundMessageState
import net.corda.p2p.linkmanager.sessions.writer.SessionWriter
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class SessionLookupImpl(
    stateManager: StateManager,
    private val sessionCache: SessionCache,
    private val sessionWriter: SessionWriter,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    stateConvertor: StateConvertor,
    checkRevocation: CheckRevocation,
    reEstablishmentMessageSender: ReEstablishmentMessageSender,
) : SessionLookup {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val stateManager = StateManagerWrapper(
        stateManager,
        sessionCache,
        stateConvertor,
        checkRevocation,
        reEstablishmentMessageSender,
    )

    override fun <T> getCachedOutboundSessions(
        messagesAndKeys: Map<String?, Collection<OutboundMessageContext<T>>>,
    ): Map<String, Collection<Pair<T, SessionEstablished>>> {
        val allCached = sessionCache.getAllPresentOutboundSessions(messagesAndKeys.keys.filterNotNull())
        return allCached.mapValues { entry ->
            val contexts = messagesAndKeys[entry.key]
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
        sessionsNotCached: Map<String?, List<OutboundMessageContext<T>>>,
    ): List<OutboundMessageState<T>> {
        return stateManager.get(sessionsNotCached.keys.filterNotNull())
            .let { states ->
                sessionsNotCached.map { (id, items) ->
                    OutboundMessageState(
                        id,
                        states[id],
                        items,
                    )
                }
            }
    }

    override fun <T> getPersistedOutboundSessionsBySessionId(
        notInboundSessions: Set<String>,
        sessionsNotCached: Map<String, List<T>>,
    ): List<Pair<List<T>, SessionManager.SessionDirection.Outbound>> {
        return stateManager.findStatesMatchingAny(
            notInboundSessions.map { getSessionIdFilter(it) },
        )
            .entries
            .mapNotNull { (key, state) ->
                val session = state.sessionState.sessionData as? Session ?: return@mapNotNull null
                val sessionId = state.managerState.metadata.toOutbound().sessionId
                sessionsNotCached[sessionId]?.let { traceables ->
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
        sessionsNotCached: Map<String, List<T>>,
    ): List<Pair<List<T>, SessionManager.SessionDirection.Inbound>> {
        return stateManager.get(sessionsNotCached.keys)
            .entries
            .mapNotNull { (sessionId, state) ->
                val session = state.sessionState.sessionData as? Session ?: return@mapNotNull null
                sessionsNotCached[sessionId]?.let { traceables ->
                    val inboundSession = sessionWriter.cacheSession(
                        SessionDirection.INBOUND,
                        state.toCounterparties(),
                        session,
                    ) as SessionManager.SessionDirection.Inbound
                    traceables to inboundSession
                }
            }
    }

    override fun <T> getSessionIdIfInboundSessionMessage(
        data: Any,
        trace: T
    ): InboundSessionMessageContext<T>? {
        TODO("Not yet implemented")
    }
}