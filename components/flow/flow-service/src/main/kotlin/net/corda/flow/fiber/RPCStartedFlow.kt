package net.corda.flow.fiber

import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.base.annotations.Suspendable

/**
 * Represents a flow started via RPC.
 */
class RPCStartedFlow(override val logic: RPCStartableFlow, private val requestBody: String) : FlowLogicAndArgs {

    @Suspendable
    override fun invoke(): String {
        return logic.call(requestBody)
    }
}