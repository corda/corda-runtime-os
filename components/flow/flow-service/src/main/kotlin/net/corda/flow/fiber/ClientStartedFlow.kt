package net.corda.flow.fiber

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.base.annotations.Suspendable

/**
 * Represents a flow started via Remote Client.
 */
class ClientStartedFlow(override val logic: ClientStartableFlow, private val requestBody: ClientRequestBody) : FlowLogicAndArgs {

    @Suspendable
    override fun invoke(): String {
        return logic.call(requestBody)
    }
}