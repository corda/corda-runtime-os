package net.corda.simulator.runtime.testflows

import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.base.annotations.Suspendable

class NonConstructableFlow(private val cordaDoesNotKnowWhatGoesHere : String) : ClientStartableFlow {
    @Suspendable
    override fun call(requestBody: RestRequestBody): String = cordaDoesNotKnowWhatGoesHere
}