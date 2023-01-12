package net.cordapp.testing.smoketests.virtualnode

import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.RestStartableFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger

class ReturnAStringFlow : RestStartableFlow {

    private companion object {
        val log = contextLogger()
    }

    @Suspendable
    override fun call(requestBody: RestRequestBody): String {
        log.info("ReturnAStringFlow starting...")
        return "force-uploaded-cpi"
    }
}
