package net.corda.flow.manager.impl.runner

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowFiber

interface FlowRunner {

    fun runFlow(checkpoint: Checkpoint, inputEvent: FlowEvent, flowContinuation: FlowContinuation): FlowFiber<*>
}