package net.corda.simulator.runtime.testflows

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow

class NonSuspendableFlow : ClientStartableFlow {
    override fun call(requestBody: ClientRequestBody): String = ""
}