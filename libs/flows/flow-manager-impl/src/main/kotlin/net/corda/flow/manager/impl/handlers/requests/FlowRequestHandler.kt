package net.corda.flow.manager.impl.handlers.requests

import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.manager.FlowEventContext
import net.corda.flow.statemachine.requests.FlowIORequest

interface FlowRequestHandler<T : FlowIORequest<*>> {

    val type: Class<T>

    fun postProcess(context: FlowEventContext<Any>, request: T): FlowEventContext<Any>
}

fun FlowEventContext<*>.setCheckpointWaitingFor(value: Any?) {
    checkpoint!!.flowState.waitingFor = WaitingFor(value)
}