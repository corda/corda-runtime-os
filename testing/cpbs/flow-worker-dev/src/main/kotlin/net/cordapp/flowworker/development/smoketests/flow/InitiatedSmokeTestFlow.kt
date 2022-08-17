package net.cordapp.flowworker.development.smoketests.flow

import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.application.messaging.unwrap
import net.corda.v5.base.annotations.Suspendable
import net.cordapp.flowworker.development.smoketests.flow.messages.InitiatedSmokeTestMessage

@InitiatedBy(protocol = "smoke-test-protocol")
class InitiatedSmokeTestFlow : ResponderFlow {

    @Suspendable
    override fun call(session: FlowSession) {
        val received = session.receive<InitiatedSmokeTestMessage>().unwrap { it }
        session.send(InitiatedSmokeTestMessage("echo:${received.message}"))
    }
}