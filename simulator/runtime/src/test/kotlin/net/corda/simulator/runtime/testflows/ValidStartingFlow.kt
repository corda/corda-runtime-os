package net.corda.simulator.runtime.testflows

import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.base.annotations.Suspendable

@InitiatingFlow(protocol = "valid")
class ValidStartingFlow : ClientStartableFlow {
    @Suspendable
    override fun call(requestBody: RestRequestBody): String {
        return ""
    }
}