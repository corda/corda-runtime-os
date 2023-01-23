package net.cordapp.testing.smoketests.virtualnode

import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger

class NoChangelogFlow : ClientStartableFlow {

    private companion object {
        val log = contextLogger()
    }

    @Suspendable
    override fun call(requestBody: RestRequestBody): String {
        log.info("NoChangelogFlow starting...")
        return "NO_CHANGELOG_FLOW_COMPLETE"
    }
}
