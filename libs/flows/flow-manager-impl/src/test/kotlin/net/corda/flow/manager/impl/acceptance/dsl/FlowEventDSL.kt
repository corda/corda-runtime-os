package net.corda.flow.manager.impl.acceptance.dsl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.state.Checkpoint
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.manager.fiber.FlowIORequest
import net.corda.flow.manager.impl.FlowEventProcessorImpl
import net.corda.flow.manager.impl.handlers.events.StartRPCFlowEventHandler
import net.corda.flow.manager.impl.handlers.events.WakeUpEventHandler
import net.corda.flow.manager.impl.handlers.requests.CloseSessionsRequestHandler
import net.corda.flow.manager.impl.handlers.requests.FlowFailedRequestHandler
import net.corda.flow.manager.impl.handlers.requests.FlowFinishedRequestHandler
import net.corda.flow.manager.impl.handlers.requests.ForceCheckpointRequestHandler
import net.corda.flow.manager.impl.handlers.requests.GetFlowInfoRequestHandler
import net.corda.flow.manager.impl.handlers.requests.ReceiveRequestHandler
import net.corda.flow.manager.impl.handlers.requests.SendAndReceiveRequestHandler
import net.corda.flow.manager.impl.handlers.requests.SendRequestHandler
import net.corda.flow.manager.impl.handlers.requests.SleepRequestHandler
import net.corda.flow.manager.impl.handlers.requests.SubFlowFailedRequestHandler
import net.corda.flow.manager.impl.handlers.requests.SubFlowFinishedRequestHandler
import net.corda.flow.manager.impl.handlers.requests.WaitForSessionConfirmationsRequestHandler
import net.corda.flow.manager.impl.pipeline.factory.FlowEventPipelineFactoryImpl
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import java.time.Instant
import java.util.UUID

fun flowEventDSL(dsl: FlowEventDSL.() -> Unit) {
    FlowEventDSL().run(dsl)
}

class FlowEventDSL {

    private val flowRunner = MockFlowRunner()
    private val processor = FlowEventProcessorImpl(FlowEventPipelineFactoryImpl(flowRunner, flowEventHandlers, flowRequestHandlers))

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
        flowRunner.addFlowFiber(fiber)
        return fiber
    }

    fun flowFiber(flowId: String = UUID.randomUUID().toString(), fiber: MockFlowFiber.() -> Unit): MockFlowFiber {
        return MockFlowFiber(flowId).apply {
            fiber(this)
            flowRunner.addFlowFiber(this)
        }
    }

    fun startedFlowFiber(flowId: String = UUID.randomUUID().toString(), fiber: MockFlowFiber.() -> Unit): MockFlowFiber {
        val (mockFlowFiber, response) = startFlow(flowId)
        updateDSLStateWithEventResponse(flowId, response)
        fiber(mockFlowFiber)
        return mockFlowFiber
    }

    fun processOne(): StateAndEventProcessor.Response<Checkpoint> {
        val input = checkNotNull(inputFlowEvents.removeFirstOrNull()) { "No input flow events have been setup" }
        val event = when (input) {
            ProcessLastOutputFlowEvent -> {
                checkNotNull(outputFlowEvents.removeFirstOrNull()) {
                    "Trying to process an output flow event returned from the processor but none exist"
                }
            }
            is FlowEvent -> input
            else -> {
                throw IllegalStateException("Must be a ${FlowEvent::class.simpleName} or ${ProcessLastOutputFlowEvent::class.simpleName}")
            }
        }
        val flowId = event.flowKey.flowId
        return processor.onNext(
            state = checkpoints[flowId],
            event = Record(Schemas.Flow.FLOW_EVENT_TOPIC, event.flowKey, event)
        ).also { updateDSLStateWithEventResponse(flowId, it) }
    }

    fun processAll(): List<StateAndEventProcessor.Response<Checkpoint>> {
        check(inputFlowEvents.isNotEmpty()) { "No input flow events have been setup" }
        return inputFlowEvents.toList().map { processOne() }
    }

    private fun startFlow(flowId: String): Pair<MockFlowFiber, StateAndEventProcessor.Response<Checkpoint>> {
        val startRPCFlowPayload = StartRPCFlow.newBuilder()
            .setClientId("client id")
            .setCpiId("cpi id")
            .setFlowClassName("flow class name")
            .setRpcUsername(HoldingIdentity("x500 name", "group id"))
            .setTimestamp(Instant.now())
            .setJsonArgs(" { \"json\": \"args\" }")
            .build()

        val key = FlowKey(flowId, HoldingIdentity("x500 name", "group id"))

        val event = FlowEvent(key, startRPCFlowPayload)

        val fiber = MockFlowFiber(flowId).apply {
            queueSuspension(FlowIORequest.ForceCheckpoint)
            flowRunner.addFlowFiber(this)
        }
        return fiber to processor.onNext(state = null, event = Record(Schemas.Flow.FLOW_EVENT_TOPIC, key, event))
    }

    private fun updateDSLStateWithEventResponse(flowId: String, response: StateAndEventProcessor.Response<Checkpoint>) {
        response.updatedState?.let { checkpoint -> checkpoints[flowId] = checkpoint } ?: checkpoints.remove(flowId)
        outputFlowEvents.addAll(response.filterOutputFlowTopicEvents())
    }

    private object ProcessLastOutputFlowEvent
}

// Must be updated when new flow event handlers are added
private val flowEventHandlers = listOf(
    StartRPCFlowEventHandler(),
    WakeUpEventHandler()
)

// Must be updated when new flow request handlers are added
private val flowRequestHandlers = listOf(
    CloseSessionsRequestHandler(),
    FlowFailedRequestHandler(),
    FlowFinishedRequestHandler(),
    ForceCheckpointRequestHandler(),
    GetFlowInfoRequestHandler(),
    ReceiveRequestHandler(),
    SendAndReceiveRequestHandler(),
    SendRequestHandler(),
    SleepRequestHandler(),
    SubFlowFailedRequestHandler(),
    SubFlowFinishedRequestHandler(),
    WaitForSessionConfirmationsRequestHandler()
)