package net.corda.simulator.runtime.messaging

import net.corda.v5.base.types.MemberX500Name
import java.util.concurrent.LinkedBlockingQueue

interface SessionFactory {
    fun createSessions(counterparty: MemberX500Name, flowContext: FlowContext,
                       flowContextProperties: SimFlowContextProperties): SessionPair
}

class BaseSessionFactory : SessionFactory {
    override fun createSessions(counterparty: MemberX500Name, flowContext: FlowContext,
                                flowContextProperties: SimFlowContextProperties): SessionPair {
        val fromInitiatorToResponder = LinkedBlockingQueue<Any>()
        val fromResponderToInitiator = LinkedBlockingQueue<Any>()
        val initiatorSession = BaseInitiatorFlowSession(
            flowContext.copy(member = counterparty),
            fromResponderToInitiator,
            fromInitiatorToResponder,
            flowContextProperties
        )
        val recipientSession = BaseResponderFlowSession(
            flowContext,
            fromInitiatorToResponder,
            fromResponderToInitiator,
            flowContextProperties
        )
        return SessionPair(initiatorSession, recipientSession)
    }
}

data class SessionPair(
    val initiatorSession: InitiatorFlowSession,
    val responderSession: ResponderFlowSession
    )
