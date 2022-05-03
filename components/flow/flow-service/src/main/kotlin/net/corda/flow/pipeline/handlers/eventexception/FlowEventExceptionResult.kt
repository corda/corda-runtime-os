package net.corda.flow.pipeline.handlers.eventexception

import net.corda.flow.pipeline.FlowEventContext

data class FlowEventExceptionResult(
    val updatedContext: FlowEventContext<Any>,
    val wasHandled: Boolean
)