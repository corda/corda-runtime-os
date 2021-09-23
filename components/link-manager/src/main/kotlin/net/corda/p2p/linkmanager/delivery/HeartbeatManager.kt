package net.corda.p2p.linkmanager.delivery

import net.corda.p2p.app.HoldingIdentity
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.sessions.SessionManager

interface HeartbeatManager {
    fun sessionMessageAdded(uniqueId: String, destroyPendingSession: (sessionId: String) -> Any)
    fun sessionMessageAcknowledged(uniqueId: String)
    fun messageSent(messageId: String,
                    source: HoldingIdentity,
                    dest: HoldingIdentity,
                    session: Session,
                    destroySession: (sessionKey: SessionManager.SessionKey) -> Any
    )
    fun messageAcknowledged(messageId: String, session: Session, destroySession: (sessionKey: SessionManager.SessionKey) -> Any)
}