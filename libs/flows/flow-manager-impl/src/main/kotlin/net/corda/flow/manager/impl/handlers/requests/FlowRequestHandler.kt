package net.corda.flow.manager.impl.handlers.requests

import net.corda.flow.manager.FlowEventContext
import net.corda.flow.statemachine.requests.FlowIORequest
import net.corda.data.flow.request.FlowIORequest as AvroFlowIORequest

interface FlowRequestHandler<T : FlowIORequest<*>> {

    val type: Class<T>

    fun postProcess(context: FlowEventContext<Any>, request: T): FlowEventContext<Any>
}

fun FlowEventContext<*>.setCheckpointFlowIORequest(request: Any?) {
    checkpoint!!.flowState.flowIORequest = AvroFlowIORequest(request)
}