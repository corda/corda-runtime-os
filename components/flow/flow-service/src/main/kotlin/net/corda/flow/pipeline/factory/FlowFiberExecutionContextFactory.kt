package net.corda.flow.pipeline.factory

import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.pipeline.events.FlowEventContext

/**
 * [FlowFiberExecutionContextFactory] Creates a new, initialized instance of [FlowFiberExecutionContext].
 */
interface FlowFiberExecutionContextFactory {

    /**
     * Creates a new, initialized instance of [FlowFiberExecutionContext]
     *
     * @param context The [FlowEventContext] contains contextual information used for creating the execution context.

     * @return The initialized [FlowFiberExecutionContext].
     */
    fun createFiberExecutionContext(
        context: FlowEventContext<Any>
    ): FlowFiberExecutionContext
}
