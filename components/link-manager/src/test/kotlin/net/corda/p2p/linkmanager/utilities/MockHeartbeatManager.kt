package net.corda.p2p.linkmanager.utilities

import net.corda.p2p.app.HoldingIdentity
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.delivery.HeartbeatManager
import net.corda.p2p.linkmanager.sessions.SessionManager

class MockHeartbeatManager: HeartbeatManager {

    val addedSessionMessages = HashSet<String>()
    val ackedSessionMessages = HashSet<String>()
    val addedMessages = HashMap<String, MessageInfo>()
    val ackedMessages = HashSet<String>()

    data class MessageInfo(val source: HoldingIdentity,
                           val dest: HoldingIdentity,
                           val session: Session)

    override fun sessionMessageAdded(uniqueId: String, destroyPendingSession: (sessionId: String) -> Any) {
        addedSessionMessages.add(uniqueId)
    }

    override fun sessionMessageAcknowledged(uniqueId: String) {
        ackedSessionMessages.add(uniqueId)
    }

    override fun messageSent(
        messageId: String,
        source: HoldingIdentity,
        dest: HoldingIdentity,
        session: Session,
        destroySession: (sessionKey: SessionManager.SessionKey) -> Any
    ) {
        addedMessages[messageId] = MessageInfo(source, dest, session)
    }

    override fun messageAcknowledged(messageId: String, session: Session, destroySession: (sessionKey: SessionManager.SessionKey) -> Any) {
        ackedMessages.add(messageId)
    }
}