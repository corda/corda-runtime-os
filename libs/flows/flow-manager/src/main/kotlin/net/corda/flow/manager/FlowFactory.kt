package net.corda.flow.manager

import net.corda.internal.application.cordapp.Cordapp
import net.corda.v5.application.flows.Flow


interface FlowFactory {
    fun createFlow(flowName: String, args: List<Any?>): Pair<Flow<*>, Cordapp?>
    fun createInitiatedFlow(initatorFlowName: String, session: FlowSession): Pair<Flow<*>, Cordapp?>
    fun getCordappForFlow(flowLogic: Flow<*>): Cordapp?
}
