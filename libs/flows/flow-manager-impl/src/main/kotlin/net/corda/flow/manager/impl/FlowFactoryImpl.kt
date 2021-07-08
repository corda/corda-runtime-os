package net.corda.flow.manager.impl

import net.corda.flow.manager.FlowFactory
import net.corda.internal.application.cordapp.Cordapp
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowSession
import net.corda.v5.base.util.contextLogger

class FlowFactoryImpl(private val cordapps: List<Cordapp>) : FlowFactory {
    companion object {
        val log = contextLogger()
    }

    override fun createFlow(flowName: String, args: List<Any?>): Pair<Flow<*>, Cordapp?> {
        TODO("Not yet implemented")
    }

    override fun createInitiatedFlow(initatorFlowName: String, session: FlowSession): Pair<Flow<*>, Cordapp?> {
        TODO("Not yet implemented")
    }

    override fun getCordappForFlow(flowLogic: Flow<*>): Cordapp? {
        TODO("Not yet implemented")
    }
}
