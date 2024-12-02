package net.corda.p2p.linkmanager.sessions.writer

import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.sessions.SessionCache
import net.corda.p2p.linkmanager.sessions.SessionManager

internal class SessionWriter(
    private val sessionCache: SessionCache,
) {
    /**
     * Caches an inbound session.
     *
     * @param counterparties The counterparties of the session.
     * @param session The session we want to cache.
     */
    fun cacheInboundSession(
        counterparties: SessionManager.Counterparties,
        session: Session,
    ): SessionManager.SessionDirection.Inbound {
        val inboundSession = SessionManager.SessionDirection.Inbound(counterparties, session)
        sessionCache.putInboundSession(inboundSession)
        return inboundSession
    }

    /**
     * Caches an outbound session.
     *
     * @param key The key for the session.
     * @param counterparties The counterparties of the session.
     * @param session The session we want to cache.
     */
    fun cacheOutboundSession(
        key: String,
        counterparties: SessionManager.Counterparties,
        session: Session,
    ): SessionManager.SessionDirection.Outbound {
        val outboundSession = SessionManager.SessionDirection.Outbound(counterparties, session)
        sessionCache.putOutboundSession(key, outboundSession)
        return outboundSession
    }
}