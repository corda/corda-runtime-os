package net.corda.p2p.linkmanager.sessions

import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.SimpleDominoTile
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.virtualnode.HoldingIdentity

internal class StatefulSessionManagerImpl(coordinatorFactory: LifecycleCoordinatorFactory): SessionManager {
    sealed class StatefulSessionStatus {
        data class SendSessionNegotiationMessage(val message: OutboundNegotiationMessage): StatefulSessionStatus()
        object SessionPending: StatefulSessionStatus()
        data class SessionReady(val session: Session): StatefulSessionStatus()
    }

    sealed class OutboundNegotiationMessage {
        data class InitiatorHello(val initiatorHelloMessage: InitiatorHelloMessage): OutboundNegotiationMessage()
        data class InitiatorHandshake(val initiatorHandshakeMessage: InitiatorHandshakeMessage): OutboundNegotiationMessage()
    }

    override fun processOutboundMessages(
        messages: List<AuthenticatedMessageAndKey>
    ): List<SessionManager.SessionState> {
        return emptyList()
    }

    override fun getSessionsById(uuids: List<String>): List<SessionManager.SessionDirection> {
        return emptyList()
    }

    override fun processSessionMessages(messages: List<LinkInMessage>): List<LinkOutMessage?> {
        return emptyList()
    }

    override fun inboundSessionEstablished(sessionId: String) {
        //Not needed by the Stateful Session Manager
        return
    }

    override fun messageAcknowledged(sessionId: String) {
        //To be implemented in CORE-18730
        return
    }

    override fun dataMessageReceived(sessionId: String, source: HoldingIdentity, destination: HoldingIdentity) {
        //No heartbeat manager in the stateful session manager
        return
    }

    override fun dataMessageSent(session: Session) {
        return
    }

    override val dominoTile = SimpleDominoTile(this::class.java.simpleName, coordinatorFactory)
}