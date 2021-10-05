package net.corda.p2p.linkmanager.utilities

import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.linkmanager.sessions.SessionManager

class MockSessionManager: SessionManager {

    val addedMessages = HashMap<String, MessageInfo>()
    val ackedMessages = HashSet<SessionManager.SessionKey>()

    data class MessageInfo(val key: SessionManager.SessionKey,
                           val session: Session)

    override fun processOutboundFlowMessage(message: AuthenticatedMessageAndKey): SessionManager.SessionState {
        TODO("Not yet implemented")
    }

    override fun getSessionById(uuid: String): SessionManager.SessionDirection {
        TODO("Not yet implemented")
    }

    override fun processSessionMessage(message: LinkInMessage): LinkOutMessage? {
        TODO("Not yet implemented")
    }

    override fun inboundSessionEstablished(sessionId: String) {
        TODO("Not yet implemented")
    }

    override fun dataMessageSent(messageAndKey: AuthenticatedMessageAndKey, session: Session) {
        addedMessages[messageAndKey.message.header.messageId] = MessageInfo(
            SessionManager.SessionKey(
                messageAndKey.message.header.source.toHoldingIdentity(),
                messageAndKey.message.header.destination.toHoldingIdentity()
            ), session)
    }

    override fun messageAcknowledged(sessionKey: SessionManager.SessionKey) {
        ackedMessages.add(sessionKey)
    }


    override val isRunning: Boolean
        get() = TODO("Not yet implemented")

    override fun start() {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }
}