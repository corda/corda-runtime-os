package net.corda.p2p.linkmanager.sessions

import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.LinkOutMessage
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.SimpleDominoTile
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.virtualnode.HoldingIdentity

internal class StatefulSessionManagerImpl(coordinatorFactory: LifecycleCoordinatorFactory): SessionManager {
    override fun <T> processOutboundMessages(
        wrappedMessages: Collection<T>,
        getMessage: (T) -> AuthenticatedMessageAndKey
    ): Collection<Pair<T, SessionManager.SessionState>> {
        return emptyList()
    }

    override fun getSessionById(
        uuid: String
    ): SessionManager.SessionDirection {
        //To be implemented in CORE-18630 (getting the outbound sessions) + CORE-18631 (getting the inbound sessions).
        return SessionManager.SessionDirection.NoSession
    }

    override fun processSessionMessage(
        message: LinkInMessage
    ): LinkOutMessage? {
        /*To be implemented CORE-18631 (process InitiatorHelloMessage, InitiatorHandshakeMessage)
         * and CORE-18630 (process ResponderHello + process ResponderHandshake)
         * */
        return null
    }

    override fun messageAcknowledged(sessionId: String) {
        //To be implemented in CORE-18730
        return
    }

    override fun inboundSessionEstablished(sessionId: String) {
        //Not needed by the Stateful Session Manager
        return
    }

    override fun dataMessageReceived(sessionId: String, source: HoldingIdentity, destination: HoldingIdentity) {
        // Not needed by the Stateful Session Manager
        return
    }

    override fun dataMessageSent(session: Session) {
        // Not needed by the Stateful Session Manager
        return
    }

    override val dominoTile = SimpleDominoTile(this::class.java.simpleName, coordinatorFactory)
}