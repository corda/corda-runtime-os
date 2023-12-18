package net.corda.p2p.linkmanager.sessions

import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage
import net.corda.libs.statemanager.api.State
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.SimpleDominoTile
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.virtualnode.HoldingIdentity

internal abstract class StatefulSessionManagerImpl(coordinatorFactory: LifecycleCoordinatorFactory): SessionManager {
    sealed class StatefulSessionStatus {
        data class SendSessionNegotiationMessage(val message: OutboundNegotiationMessage): StatefulSessionStatus()
        object SessionPending: StatefulSessionStatus()
        data class SessionReady(val session: Session): StatefulSessionStatus()
    }

    sealed class OutboundNegotiationMessage {
        data class InitiatorHello(val initiatorHelloMessage: InitiatorHelloMessage): OutboundNegotiationMessage()
        data class InitiatorHandshake(val initiatorHandshakeMessage: InitiatorHandshakeMessage): OutboundNegotiationMessage()
    }

    protected abstract fun createOrGetOutboundSession(
        counterparties: List<SessionManager.Counterparties>
    ): List<Pair<State, StatefulSessionStatus>>

    protected abstract fun getOutboundSession(counterparties: SessionManager.Counterparties): Session?

    protected abstract fun getInboundSession(sessionId: String): Session?

    protected abstract fun processSessionNegotiationMessages(messages: List<LinkInMessage>): List<Pair<State, LinkOutMessage>?>

    override fun processOutboundMessages(
        messages: List<AuthenticatedMessageAndKey>
    ): List<SessionManager.SessionState> {
        TODO("Not yet implemented")
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

    override val dominoTile = SimpleDominoTile(this::class.java.simpleName, coordinatorFactory)
}