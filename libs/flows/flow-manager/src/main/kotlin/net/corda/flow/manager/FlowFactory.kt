package net.corda.flow.manager

import net.corda.internal.application.cordapp.Cordapp
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowSession


interface FlowFactory {
    fun createFlow(flowName: String, args: List<Any?>): Pair<Flow<*>, Cordapp?>
    fun createInitiatedFlow(initatorFlowName: String, session: FlowSession): Pair<Flow<*>, Cordapp?>
    fun getCordappForFlow(flowLogic: Flow<*>): Cordapp?
}

/**
 * Thrown if the structure of a class implementing a flow is not correct. There can be several causes for this such as
 * not inheriting from [Flow], not having a valid constructor and so on.
 *
 * @property type the fully qualified name of the class that failed checks.
 */
class IllegalFlowLogicException(val type: String, msg: String) :
    IllegalArgumentException("A FlowLogicRef cannot be constructed for Flow interface of type $type: $msg") {
    constructor(type: Class<*>, msg: String) : this(type.name, msg)
}