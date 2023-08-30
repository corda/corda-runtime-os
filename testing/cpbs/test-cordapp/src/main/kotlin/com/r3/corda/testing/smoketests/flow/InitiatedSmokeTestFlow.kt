package com.r3.corda.testing.smoketests.flow

import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import com.r3.corda.testing.smoketests.flow.messages.InitiatedSmokeTestMessage
import org.slf4j.LoggerFactory

@InitiatedBy(protocol = "smoke-test-protocol")
class InitiatedSmokeTestFlow : ResponderFlow {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suspendable
    override fun call(session: FlowSession) {
        log.info("Starting initiated flow")
        val received = session.receive(InitiatedSmokeTestMessage::class.java)
        session.send(InitiatedSmokeTestMessage("echo:${received.message}"))
    }
}
