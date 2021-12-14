package net.corda.flow.manager.impl

import net.corda.flow.statemachine.FlowContinuation
import net.corda.flow.manager.impl.handlers.events.FlowEventHandler
import net.corda.flow.statemachine.requests.FlowIORequest

data class FlowEventPipeline(
    val context: FlowEventContext<Any>,
    val handler: FlowEventHandler<Any>,
    val input: FlowContinuation = FlowContinuation.Continue,
    val output: FlowIORequest<*>? = null
)