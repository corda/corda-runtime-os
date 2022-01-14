package net.corda.flow.manager.impl.runner

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowFiber

/**
 * [FlowRunner] starts or resumes [FlowFiber]s.
 */
interface FlowRunner {

    /**
     * Starts or resumes a [FlowFiber] depending on the [inputEvent] and [FlowContinuation].
     *
     * @param checkpoint The [Checkpoint] to extract information from which is used when starting or resuming a fiber.
     * @param inputEvent The [FlowEvent] that was received and led to starting or resuming a fiber.
     * @param flowContinuation [FlowContinuation] Specifies whether a new fiber should be started or an existing fiber is resumed.
     *
     * @return The running [FlowFiber].
     */
    fun runFlow(checkpoint: Checkpoint, inputEvent: FlowEvent, flowContinuation: FlowContinuation): FlowFiber<*>
}