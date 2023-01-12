package net.corda.simulator.runtime.testflows

import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.RestStartableFlow
import net.corda.v5.base.annotations.Suspendable

@InitiatingFlow("valid")
class ValidStartingFlow : RestStartableFlow {
    @Suspendable
    override fun call(requestBody: RestRequestBody): String {
        return ""
    }
}