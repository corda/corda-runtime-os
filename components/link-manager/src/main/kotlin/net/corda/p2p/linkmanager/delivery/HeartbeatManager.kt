package net.corda.p2p.linkmanager.delivery

import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.sessions.SessionManager

interface HeartbeatManager {
    fun sessionMessageSent(
        messageId: String,
        key: SessionManager.SessionKey,
        sessionId: String,
        destroySession: (key: SessionManager.SessionKey, sessionId: String) -> Any
    )

    fun messageSent(
        messageId: String,
        key: SessionManager.SessionKey,
        session: Session,
    )

    fun messageAcknowledged(messageId: String)
}