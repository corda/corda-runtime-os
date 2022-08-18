package net.cordapp.flowworker.development.testflows

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.sendAndReceive
import net.corda.v5.application.messaging.unwrap
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name

/**
 * Used to verify handling of broken protocols in CPIs.
 */
@InitiatingFlow(protocol = "broken_protocol")
class BrokenProtocolFlow : RPCStartableFlow {

    @CordaInject
    lateinit var messaging: FlowMessaging

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        val session = messaging.initiateFlow(
            MemberX500Name(
                commonName = "Alice",
                organisation = "Alice Corp",
                locality = "LDN",
                country = "GB"
            )
        )
        session.sendAndReceive<MyClass>(MyClass("Serialize me please", 1)).unwrap { it }
        return ""
    }
}