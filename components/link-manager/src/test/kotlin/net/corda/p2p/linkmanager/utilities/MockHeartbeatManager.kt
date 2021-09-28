package net.corda.p2p.linkmanager.utilities

import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.delivery.HeartbeatManager
import net.corda.p2p.linkmanager.sessions.SessionManager

class MockHeartbeatManager: HeartbeatManager {

    val addedSessionMessages = HashSet<String>()
    val addedMessages = HashMap<String, MessageInfo>()
    val ackedMessages = HashSet<String>()

    data class MessageInfo(val key: SessionManager.SessionKey,
                           val session: Session)

    override fun sessionMessageSent(
        messageId: String,
        key: SessionManager.SessionKey,
        sessionId: String,
        destroySession: (key: SessionManager.SessionKey, sessionId: String) -> Any
    ) {
        addedSessionMessages.add(messageId)
    }

    override fun messageSent(messageId: String, key: SessionManager.SessionKey, session: Session) {
        addedMessages[messageId] = MessageInfo(key, session)
    }

    override fun messageAcknowledged(messageId: String) {
        ackedMessages.add(messageId)
    }
}