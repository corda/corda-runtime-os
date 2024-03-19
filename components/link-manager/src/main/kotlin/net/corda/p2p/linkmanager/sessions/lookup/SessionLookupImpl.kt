package net.corda.p2p.linkmanager.sessions.lookup

import net.corda.p2p.linkmanager.sessions.SessionCache
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.StatefulSessionManagerImpl

internal class SessionLookupImpl(
    private val sessionCache: SessionCache
) : SessionLookup {
    override fun getSessionBySessionId(sessionID: String): SessionManager.SessionDirection? {
        TODO("Not yet implemented")
    }

    override fun <T> getOutboundSessions(keysAndMessages: Map<String?, List<StatefulSessionManagerImpl.OutboundMessageContext<T>>>) {
        sessionCache.getByKeyIfCached()
    }
}