package net.corda.simulator.runtime.testflows

import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.base.annotations.Suspendable

class NonConstructableFlow(private val cordaDoesNotKnowWhatGoesHere : String) : RPCStartableFlow {
    @Suspendable
    override fun call(requestBody: RPCRequestData): String = cordaDoesNotKnowWhatGoesHere
}