package net.corda.flow.pipeline.handlers.eventexception

import net.corda.data.flow.event.FlowEvent
import net.corda.flow.pipeline.FlowEventContext

/**
 * The [ExceptionHandler] is responsible for processing exceptions thrown processing a [FlowEvent].
 */
interface ExceptionHandler {

    /**
     * Handles and exception thrown processing a [FlowEvent]
     *
     * @param context The [FlowEventContext] that should be modified within this processing step.
     * @param exception The [Exception] thrown.

     * @return [FlowEventExceptionResult] containing a modified [FlowEventContext] and flag indicating if the
     * exception was handled.
     */
    fun handleException(
        context: FlowEventContext<Any>,
        exception: Exception,
    ): FlowEventExceptionResult
}