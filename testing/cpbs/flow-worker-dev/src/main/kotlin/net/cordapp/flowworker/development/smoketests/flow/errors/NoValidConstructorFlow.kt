package net.cordapp.flowworker.development.smoketests.flow.errors

import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.base.annotations.Suspendable

class NoValidConstructorFlow(val input: String) : RPCStartableFlow {

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        return "This worked"
    }
}