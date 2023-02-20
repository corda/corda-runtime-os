package net.cordapp.testing.smoketests.flow

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.application.messaging.sendAndReceive
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.cordapp.testing.smoketests.flow.messages.InitiatedSmokeTestMessage
import org.slf4j.LoggerFactory

@InitiatingFlow(protocol = "subflow-protocol")
class InitiatingSubFlowSmokeTestFlow(
    private val x500Name: MemberX500Name,
    private val initiateSessionInInitiatingFlow: Boolean,
    private val message: String
) : SubFlow<InitiatedSmokeTestMessage> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call(): InitiatedSmokeTestMessage {
        log.info("SubFlow - Creating session for '${x500Name}'...")
        val session = flowMessaging.initiateFlow(x500Name)
        if (initiateSessionInInitiatingFlow) {
            log.info("SubFlow - Creating session '${session}' now sending and waiting for response ...")
            session.send(InitiatedSmokeTestMessage("Initiate"))
        }
        return flowEngine.subFlow(InlineSubFlowSmokeTestFlow(session, initiateSessionInInitiatingFlow, message))
    }
}

class InlineSubFlowSmokeTestFlow(
    private val session: FlowSession,
    private val initiateSessionInInitiatingFlow: Boolean,
    private val message: String
) : SubFlow<InitiatedSmokeTestMessage> {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suspendable
    override fun call(): InitiatedSmokeTestMessage {
        if (!initiateSessionInInitiatingFlow) {
            log.info("SubFlow - Creating session '${session}' now sending and waiting for response ...")
            session.send(InitiatedSmokeTestMessage("Initiate"))
        }
        val initiatedSmokeTestMessage =
            session.sendAndReceive<InitiatedSmokeTestMessage>(InitiatedSmokeTestMessage(message))
        log.info("SubFlow - Received response from session '$session'.")
        return initiatedSmokeTestMessage
    }
}

@InitiatedBy(protocol = "subflow-protocol")
class InitiatingSubFlowResponderSmokeTestFlow : ResponderFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suspendable
    override fun call(session: FlowSession) {
        session.receive<InitiatedSmokeTestMessage>()
        log.info("SubFlow - Initiated.")
        val received = session.receive<InitiatedSmokeTestMessage>()
        log.info("SubFlow - Received message from session '$session'.")
        session.send(InitiatedSmokeTestMessage("echo:${received.message}"))
        log.info("SubFlow - Sent message to session '$session'.")
    }
}
