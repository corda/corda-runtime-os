package net.corda.p2p.linkmanager.sessions.writer

import net.corda.data.p2p.event.SessionDirection
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.sessions.SessionManager

internal interface SessionWriter {
    fun cacheSession(
        direction: SessionDirection,
        counterparties: SessionManager.Counterparties,
        session: Session,
        key: String = "",
    ): SessionManager.SessionDirection
}