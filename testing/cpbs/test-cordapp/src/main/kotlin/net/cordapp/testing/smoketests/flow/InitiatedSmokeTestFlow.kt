package net.cordapp.testing.smoketests.flow

import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.cordapp.testing.smoketests.flow.messages.InitiatedSmokeTestMessage

@InitiatedBy(protocol = "smoke-test-protocol")
class InitiatedSmokeTestFlow : ResponderFlow {

    @Suspendable
    override fun call(session: FlowSession) {
        val received = session.receive(InitiatedSmokeTestMessage::class.java)
        session.send(InitiatedSmokeTestMessage("echo:${received.message}"))
    }
}
