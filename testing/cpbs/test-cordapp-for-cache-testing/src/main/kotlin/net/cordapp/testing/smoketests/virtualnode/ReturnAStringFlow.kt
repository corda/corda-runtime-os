package net.cordapp.testing.smoketests.virtualnode

import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory

class ReturnAStringFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("ReturnAStringFlow starting...")
        return "force-uploaded-cpi"
    }
}
