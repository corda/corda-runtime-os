package net.cordapp.testing.smoketests.virtualnode

import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger

class NoChangelogFlow : RPCStartableFlow {

    private companion object {
        val log = contextLogger()
    }

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("NoChangelogFlow starting...")
        return "NO_CHANGELOG_FLOW_COMPLETE"
    }
}
