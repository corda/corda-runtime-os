package net.corda.flow.manager.impl.runner

import net.corda.flow.manager.fiber.FlowContinuation
import net.corda.flow.manager.fiber.FlowIORequest
import net.corda.flow.manager.impl.FlowEventContext
import java.util.concurrent.Future

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
     * @return The running [FlowFiber].
     */
    fun runFlow(
        context: FlowEventContext<Any>,
        flowContinuation: FlowContinuation
    ): Future<FlowIORequest<*>>
}