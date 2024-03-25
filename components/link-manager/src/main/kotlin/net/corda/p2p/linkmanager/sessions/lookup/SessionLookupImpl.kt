package net.corda.p2p.linkmanager.sessions.lookup

import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.p2p.linkmanager.membership.lookup
import net.corda.p2p.linkmanager.sessions.SessionCache
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.couldNotFindSessionInformation
import net.corda.p2p.linkmanager.sessions.StateManagerAction
import net.corda.p2p.linkmanager.sessions.StateManagerWrapper
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory

internal class SessionLookupImpl(
    stateManager: StateManager,
    private val sessionCache: SessionCache,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
) : SessionLookup {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val stateManager = StateManagerWrapper(
        stateManager,
        sessionCache,
    )

    override fun getSessionBySessionId(sessionID: String): SessionManager.SessionDirection? {
        TODO("Not yet implemented")
    }

    override fun <T> getOutboundSessions(keysAndMessages: Map<String?, List<OutboundMessageContext<T>>>) {
        val cachedSessions = getCachedOutboundSessions(keysAndMessages)
        val notCachedSessions = (keysAndMessages - cachedSessions.keys).keys
        val sessionStates =
            if (notCachedSessions.isNotEmpty()) {
                stateManager.get(notCachedSessions.filterNotNull())
                    .let { states ->
                        notCachedSessions.map { (id, items) ->
                            OutboundMessageState(
                                id,
                                states[id],
                                items,
                            )
                        }
                    }
            } else {
                OutboundMessageState(
                    null,
                    null,
                    null,
                )
            }
    }

    private fun <T> getCachedOutboundSessions(
        messagesAndKeys: Map<String?, Collection<OutboundMessageContext<T>>>,
    ): Map<String, Collection<Pair<T, SessionManager.SessionState.SessionEstablished>>> {
        val allCached = sessionCache.getAllPresentOutboundSessions(messagesAndKeys.keys.filterNotNull())
        return allCached.mapValues { entry ->
            val contexts = messagesAndKeys[entry.key]
            val counterparties = contexts?.firstOrNull()
                ?.message
                ?.message
                ?.getSessionCounterpartiesFromMessage() ?: return@mapValues emptyList()

            contexts.map { context ->
                context.trace to SessionManager.SessionState.SessionEstablished(entry.value.session, counterparties)
            }
        }.toMap()
    }

    private fun AuthenticatedMessage.getSessionCounterpartiesFromMessage(): SessionManager.SessionCounterparties? {
        val peer = this.header.destination
        val us = this.header.source
        val status = this.header.statusFilter
        val ourInfo = membershipGroupReaderProvider.lookup(
            us.toCorda(), us.toCorda(), MembershipStatusFilter.ACTIVE_OR_SUSPENDED
        )
        // could happen when member has pending registration or something went wrong
        if (ourInfo == null) {
            logger.warn("Could not get member information about us from message sent from $us" +
                    " to $peer with ID `${this.header.messageId}`.")
        }
        val counterpartyInfo = membershipGroupReaderProvider.lookup(us.toCorda(), peer.toCorda(), status)
        if (counterpartyInfo == null) {
            logger.couldNotFindSessionInformation(us.toCorda().shortHash, peer.toCorda().shortHash, this.header.messageId)
            return null
        }
        return SessionManager.SessionCounterparties(
            us.toCorda(),
            peer.toCorda(),
            status,
            counterpartyInfo.serial,
            isCommunicationBetweenMgmAndMember(ourInfo, counterpartyInfo)
        )
    }

    private fun isCommunicationBetweenMgmAndMember(ourInfo: MemberInfo?, counterpartyInfo: MemberInfo): Boolean {
        return counterpartyInfo.isMgm || ourInfo?.isMgm == true
    }

    data class OutboundMessageContext<T>(
        val trace: T,
        val message: AuthenticatedMessageAndKey,
    )

    private data class OutboundMessageState<T>(
        val key: String?,
        val state: State?,
        val messages: Collection<OutboundMessageContext<T>>,
    ) {
        val first by lazy {
            messages.first()
        }
        val others by lazy {
            messages.drop(1)
        }

        fun toResults(
            sessionState: SessionManager.SessionState,
        ): Collection<OutboundMessageResults<T>> {
            return listOf(
                OutboundMessageResults(
                    key = this.key,
                    messages = this.messages,
                    action = null,
                    sessionState = sessionState,
                ),
            )
        }

        fun toResultsFirstAndOther(
            firstState: SessionManager.SessionState,
            otherStates: SessionManager.SessionState,
            action: StateManagerAction,
        ): Collection<OutboundMessageResults<T>> {
            val firstResult = OutboundMessageResults(
                key = this.key,
                messages = listOf(first),
                action = action,
                sessionState = firstState,
            )
            return if (others.isEmpty()) {
                listOf(firstResult)
            } else {
                listOf(
                    firstResult,
                    OutboundMessageResults(
                        key = this.key,
                        messages = others,
                        action = null,
                        sessionState = otherStates,
                    ),
                )
            }
        }
    }

    private data class OutboundMessageResults<T>(
        val key: String?,
        val messages: Collection<OutboundMessageContext<T>>,
        val action: StateManagerAction?,
        val sessionState: SessionManager.SessionState,
    )
}