package net.corda.testutils.flows

import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.base.annotations.Suspendable

class ValidStartingFlow : RPCStartableFlow {
    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        return ""
    }
}