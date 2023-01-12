package net.corda.flow.fiber

import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.RestStartableFlow
import net.corda.v5.base.annotations.Suspendable

/**
 * Represents a flow started via RPC.
 */
class RestStartedFlow(override val logic: RestStartableFlow, private val requestBody: RestRequestBody) : FlowLogicAndArgs {

    @Suspendable
    override fun invoke(): String {
        return logic.call(requestBody)
    }
}