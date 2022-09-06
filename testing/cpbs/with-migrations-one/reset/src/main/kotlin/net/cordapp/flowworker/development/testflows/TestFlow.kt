package net.cordapp.flowworker.development.testflows

import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.base.annotations.Suspendable

/**
 * The Test Flow exercises various basic features of a flow, this flow
 * is used as a basic flow worker smoke test.
 */
@Suppress("unused")
class TestFlow : RPCStartableFlow {
    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        // Nothing
        return "nothing"
    }
}

