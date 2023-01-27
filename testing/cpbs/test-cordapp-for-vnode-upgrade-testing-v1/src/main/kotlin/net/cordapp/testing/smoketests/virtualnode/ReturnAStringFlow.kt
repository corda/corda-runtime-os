package net.cordapp.testing.smoketests.virtualnode

import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.base.annotations.Suspendable

class ReturnAStringFlow : ClientStartableFlow {

    @Suspendable
    override fun call(requestBody: RestRequestBody): String {
        return "upgrade-test-v1"
    }
}
