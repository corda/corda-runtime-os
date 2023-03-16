package net.corda.flow.pipeline.runner

import net.corda.flow.fiber.FiberFuture
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.pipeline.events.FlowEventContext

/**
 * [FlowRunner] starts or resumes [FlowFiber]s.
 */
interface FlowRunner {

    /**
     * Starts or resumes a [FlowFiber] depending on the [FlowEventContext] and [FlowContinuation].
     *
     * @param context The [FlowEventContext] contains contextual information used for starting or resuming a flow.
     * @param flowContinuation [FlowContinuation] Specifies whether a new fiber should be started or an existing fiber is resumed.
     *
     * @return A future representing the executing FlowFiber.
     */
    fun runFlow(context: FlowEventContext<Any>, flowContinuation: FlowContinuation): FiberFuture
}
