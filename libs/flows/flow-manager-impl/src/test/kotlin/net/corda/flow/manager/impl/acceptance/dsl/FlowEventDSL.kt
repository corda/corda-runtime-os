package net.corda.flow.manager.impl.acceptance.dsl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas

fun flowEventDSL(dsl: FlowEventDSL.() -> Unit) {
    FlowEventDSL().run(dsl)
}

class FlowEventDSL {

    private val processor = mockFlowEventProcessor()

    private val inputFlowEvents = mutableListOf<Any>()

    private var checkpoints = mutableMapOf<String, Checkpoint>()
    private var outputFlowEvents = mutableListOf<FlowEvent>()

    fun input(event: FlowEvent) {
        inputFlowEvents += event
    }

    fun inputLastOutputEvent() {
        inputFlowEvents += ProcessLastOutputFlowEvent
    }

    fun flowFiber(fiber: MockFlowFiber): MockFlowFiber {
        processor.addFlowFiber(fiber)
        return fiber
    }

    fun flowFiber(flowId: String, fiber: MockFlowFiber.() -> Unit): MockFlowFiber {
        return MockFlowFiber(flowId).apply {
            fiber(this)
            processor.addFlowFiber(this)
        }
    }

    fun startedFlowFiber(flowId: String, fiber: MockFlowFiber.() -> Unit): MockFlowFiber {
        val (mockFlowFiber, response) = processor.startFlow(flowId)
        updateDSLStateWithEventResponse(flowId, response)
        fiber(mockFlowFiber)
        return mockFlowFiber
    }

    fun processOne(): StateAndEventProcessor.Response<Checkpoint> {
        val event = when (val input = checkNotNull(inputFlowEvents.removeFirstOrNull()) { "No input flow events have been setup" }) {
            ProcessLastOutputFlowEvent -> {
                checkNotNull(outputFlowEvents.removeFirstOrNull()) {
                    "Trying to process the the older output flow event returned from the processor but none exist"
                }
            }
            is FlowEvent -> input
            else -> throw IllegalStateException("Must be a ${FlowEvent::class.simpleName} or ${ProcessLastOutputFlowEvent::class.simpleName}")
        }
        val flowId = event.flowKey.flowId
        return processor.onNext(
            state = checkpoints[flowId],
            event = Record(Schemas.Flow.FLOW_EVENT_TOPIC, event.flowKey, event)
        ).also { updateDSLStateWithEventResponse(flowId, it) }
    }

    fun processAll(): List<StateAndEventProcessor.Response<Checkpoint>> {
        // Copy [inputs] and throw it away for code simplicity
        return inputFlowEvents.toList().map { processOne() }
    }

    private fun updateDSLStateWithEventResponse(flowId: String, response: StateAndEventProcessor.Response<Checkpoint>) {
        response.updatedState?.let { checkpoint -> checkpoints[flowId] = checkpoint } ?: checkpoints.remove(flowId)
        outputFlowEvents.addAll(response.filterOutputFlowTopicEvents())
    }

    private object ProcessLastOutputFlowEvent
}