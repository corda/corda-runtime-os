package net.corda.simulator.runtime.messaging

import net.corda.v5.base.types.MemberX500Name
import java.util.concurrent.LinkedBlockingQueue

interface SessionFactory {
    fun createSessions(counterparty: MemberX500Name, flowContext: FlowContext): SessionPair
}

class BaseSessionFactory : SessionFactory {
    override fun createSessions(counterparty: MemberX500Name, flowContext: FlowContext): SessionPair {
        val fromInitiatorToResponder = LinkedBlockingQueue<Any>()
        val fromResponderToInitiator = LinkedBlockingQueue<Any>()
        val initiatorSession = BaseInitiatorFlowSession(
            flowContext.copy(member = counterparty),
            fromResponderToInitiator,
            fromInitiatorToResponder
        )
        val recipientSession = BaseResponderFlowSession(
            flowContext,
            fromInitiatorToResponder,
            fromResponderToInitiator,
        )
        return SessionPair(initiatorSession, recipientSession)
    }
}

data class SessionPair(
    val initiatorSession: InitiatorFlowSession,
    val responderSession: ResponderFlowSession
    )
