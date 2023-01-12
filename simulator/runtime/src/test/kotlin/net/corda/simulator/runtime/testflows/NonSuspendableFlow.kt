package net.corda.simulator.runtime.testflows

import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.RestStartableFlow

class NonSuspendableFlow : RestStartableFlow {
    override fun call(requestBody: RestRequestBody): String = ""
}