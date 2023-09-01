package net.corda.flow.pipeline

import net.corda.data.flow.event.FlowEvent
import net.corda.flow.pipeline.events.FlowEventContext

/**
 * [FlowEventPipeline] encapsulates the pipeline steps that are executed when a [FlowEvent] is received by a [FlowEventProcessor].
 */
interface FlowEventPipeline {

    /**
     * The current pipeline processing context
     */
    val context: FlowEventContext<Any>

    /**
     * Performs flow event pre-processing on the pipeline.
     *
     * @return The updated pipeline instance.
     */
    fun eventPreProcessing(): FlowEventPipeline

    /**
     * Performs validation of the flow operational status on the virtual node. If the virtual node has flow operation de-activated, further
     * activity for flows in this virtual node will be killed.
     *
     * @throws 'FlowMarkedForKillException' if the virtual node does not have active flow operation
     * @return The pipeline instance.
     */
    fun virtualNodeFlowOperationalChecks(): FlowEventPipeline

    /**
     * Runs the user code a number of times, and processes any output requests that the flow makes.
     *
     * @param timeout Time in milliseconds to wait before timing out running the fiber. Note that this applies per
     * fiber execution, so this function may block for longer than this if the fiber is run multiple times.
     * @return The updated pipeline instance
     */
    fun executeFlow(timeout: Long): FlowEventPipeline

    /**
     * Performs post-processing that should always execute.
     *
     * @return The updated pipeline instance.
     */
    fun globalPostProcessing(): FlowEventPipeline
}
