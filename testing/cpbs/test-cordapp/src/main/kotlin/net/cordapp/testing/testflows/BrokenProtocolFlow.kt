package net.cordapp.testing.testflows

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.RestStartableFlow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.sendAndReceive
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name

/**
 * Used to verify handling of broken protocols in CPIs.
 */
@InitiatingFlow(protocol = "broken_protocol")
class BrokenProtocolFlow : RestStartableFlow {

    @CordaInject
    lateinit var messaging: FlowMessaging

    @Suspendable
    override fun call(requestBody: RestRequestBody): String {
        val session = messaging.initiateFlow(
            MemberX500Name(
                commonName = "Alice",
                organization = "Alice Corp",
                locality = "LDN",
                country = "GB"
            )
        )
        session.sendAndReceive<MyClass>(MyClass("Serialize me please", 1))
        return ""
    }
}
