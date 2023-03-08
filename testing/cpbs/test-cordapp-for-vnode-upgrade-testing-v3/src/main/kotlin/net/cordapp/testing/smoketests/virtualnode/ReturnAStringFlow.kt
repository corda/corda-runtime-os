package net.cordapp.testing.smoketests.virtualnode

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.base.annotations.Suspendable

class ReturnAStringFlow : ClientStartableFlow {

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        return "upgrade-test-v3"
    }
}
