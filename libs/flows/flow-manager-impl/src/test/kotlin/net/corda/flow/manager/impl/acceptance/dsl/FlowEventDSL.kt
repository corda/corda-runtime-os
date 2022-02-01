package net.corda.flow.manager.impl.acceptance.dsl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import java.util.UUID

fun flowEventDSL(dsl: FlowEventDSL.() -> Unit) {
    FlowEventDSLImpl().run(dsl)
}

// need to take the output of a processing step and process any flow events for each reprocess marker there is
interface FlowEventDSL {

    // currently im adding the flow event
    // might want to switch to allowing input payload
    // if doing payload i need to pass in flow key or flow id for simplicity
    fun input(event: FlowEvent)

    fun inputLastOutputEvent()

    fun flowFiber(fiber: MockFlowFiber): MockFlowFiber

    fun flowFiber(flowId: String = UUID.randomUUID().toString(), fiber: MockFlowFiber.() -> Unit): MockFlowFiber

    fun startedFlowFiber(flowId: String = UUID.randomUUID().toString(), fiber: MockFlowFiber.() -> Unit): MockFlowFiber

    fun processOne(): StateAndEventProcessor.Response<Checkpoint>

    // could return just the last state instead if that is more useful
    fun processAll(): List<StateAndEventProcessor.Response<Checkpoint>>
}

// not thread safe
class FlowEventDSLImpl : FlowEventDSL {

    private val processor = mockFlowEventProcessor()

    private val inputFlowEvents = mutableListOf<Any>()

    private var checkpoints = mutableMapOf<String, Checkpoint>()
    private var outputFlowEvents = mutableListOf<FlowEvent>()

    override fun input(event: FlowEvent) {
        inputFlowEvents += event
    }

    override fun inputLastOutputEvent() {
        inputFlowEvents += ProcessLastOutputFlowEvent
    }

    override fun flowFiber(fiber: MockFlowFiber): MockFlowFiber {
        processor.addFlowFiber(fiber)
        return fiber
    }

    override fun flowFiber(flowId: String, fiber: MockFlowFiber.() -> Unit): MockFlowFiber {
        return MockFlowFiber(flowId).apply {
            fiber(this)
            processor.addFlowFiber(this)
        }
    }

    override fun startedFlowFiber(flowId: String, fiber: MockFlowFiber.() -> Unit): MockFlowFiber {
        val (mockFlowFiber, response) = processor.startFlow(flowId)
        updateDSLStateWithEventResponse(flowId, response)
        fiber(mockFlowFiber)
        return mockFlowFiber
    }

    override fun processOne(): StateAndEventProcessor.Response<Checkpoint> {
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

    override fun processAll(): List<StateAndEventProcessor.Response<Checkpoint>> {
        // Copy [inputs] and throw it away for code simplicity
        return inputFlowEvents.toList().map { processOne() }
    }

    private fun updateDSLStateWithEventResponse(flowId: String, response: StateAndEventProcessor.Response<Checkpoint>) {
        response.updatedState?.let { checkpoint -> checkpoints[flowId] = checkpoint } ?: checkpoints.remove(flowId)
        outputFlowEvents.addAll(response.filterOutputFlowTopicEvents())
    }

    private object ProcessLastOutputFlowEvent
}