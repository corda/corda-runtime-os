package net.corda.p2p.linkmanager.sessions.writer

import net.corda.data.p2p.event.SessionDirection
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.sessions.SessionCache
import net.corda.p2p.linkmanager.sessions.SessionManager

internal class SessionWriterImpl(
    private val sessionCache: SessionCache,
): SessionWriter {
    override fun cacheSession(
        direction: SessionDirection,
        counterparties: SessionManager.Counterparties,
        session: Session,
        key: String,
    ): SessionManager.SessionDirection {
        return when (direction) {
            SessionDirection.INBOUND -> cacheInboundSession(counterparties, session)
            SessionDirection.OUTBOUND -> cacheOutboundSession(key, counterparties, session)
        }
    }

    private fun cacheInboundSession(
        counterparties: SessionManager.Counterparties,
        session: Session,
    ): SessionManager.SessionDirection.Inbound {
        val inboundSession = SessionManager.SessionDirection.Inbound(counterparties, session)
        sessionCache.putInboundSession(inboundSession)
        return inboundSession
    }

    private fun cacheOutboundSession(
        key: String,
        counterparties: SessionManager.Counterparties,
        session: Session,
    ): SessionManager.SessionDirection.Outbound {
        val outboundSession = SessionManager.SessionDirection.Outbound(counterparties, session)
        sessionCache.putOutboundSession(key, outboundSession)
        return outboundSession
    }
}