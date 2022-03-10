package net.corda.flow.pipeline.runner

import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
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
    fun runFlow(context: FlowEventContext<Any>, flowContinuation: FlowContinuation): Future<FlowIORequest<*>>
}