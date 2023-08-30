package net.corda.flow.testing.context

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowIORequest
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record

class TestRun(
    val event: Record<String, FlowEvent>
) {
    val ioRequests: MutableList<FlowIORequest<*>> = mutableListOf()
    var response: StateAndEventProcessor.Response<Checkpoint>? = null
    var flowContinuation: FlowContinuation? = null
}