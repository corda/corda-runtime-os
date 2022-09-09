package net.cordacon.example

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name

@InitiatingFlow("absence-call")
class AbsenceSubFlow(val counterparty: MemberX500Name) : SubFlow<String> {

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call(): String {
        val session = flowMessaging.initiateFlow(counterparty)
        session.send(RollCallRequest(counterparty.toString()))
        return session.receive(RollCallResponse::class.java).unwrap {it}.response
    }
}