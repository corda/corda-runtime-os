package com.r3.corda.testing.testflows

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name

/**
 * Used to verify handling of broken protocols in CPIs.
 */
@InitiatingFlow(protocol = "broken_protocol")
class BrokenProtocolFlow : ClientStartableFlow {

    @CordaInject
    lateinit var messaging: FlowMessaging

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val session = messaging.initiateFlow(
            MemberX500Name("Alice", "Alice Corp", "LDN", "GB")
        )
        session.sendAndReceive(MyClass::class.java, MyClass("Serialize me please", 1))
        return ""
    }
}
