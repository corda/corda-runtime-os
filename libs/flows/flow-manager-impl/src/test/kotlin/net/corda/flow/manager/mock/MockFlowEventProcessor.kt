package net.corda.flow.manager.mock

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.state.Checkpoint
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.manager.FlowEventProcessor
import net.corda.flow.manager.fiber.FlowContinuation
import net.corda.flow.manager.fiber.FlowIORequest
import net.corda.flow.manager.impl.FlowEventContext
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
import net.corda.flow.manager.impl.runner.FlowRunner
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.v5.base.util.uncheckedCast
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

fun flowEventDSL(dsl: FlowEventDSL.() -> Unit) {
    FlowEventDSLImpl().run(dsl)
}

fun mockFlowEventProcessor(): MockFlowEventProcessor {
    val mockFlowRunner = MockFlowRunner()
    val flowEventPipelineFactory = FlowEventPipelineFactoryImpl(
        mockFlowRunner,
        flowEventHandlers,
        flowRequestHandlers
    )
    return MockFlowEventProcessor(FlowEventProcessorImpl(flowEventPipelineFactory), mockFlowRunner)
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

class MockFlowRunner : FlowRunner {

    private var fibers = mutableMapOf<String, MockFlowFiber>()

    override fun runFlow(
        context: FlowEventContext<Any>,
        flowContinuation: FlowContinuation
    ): Future<FlowIORequest<*>> {
        val flowId = checkNotNull(context.checkpoint?.flowKey?.flowId) { "No flow id is set, context: $context" }
        val fiber = checkNotNull(fibers[flowId]) { "No flow with flow id: $flowId has been set up within the mocking framework" }
        return CompletableFuture.completedFuture(fiber.dequeueSuspension())
    }

    fun addFlowFiber(fiber: MockFlowFiber) {
        fibers[fiber.flowId] = fiber
    }
}

class MockFlowEventProcessor(delegate: FlowEventProcessor, private val flowRunner: MockFlowRunner) : FlowEventProcessor by delegate {

    fun startFlow(
        flowId: String = UUID.randomUUID().toString(),
        clientId: String = "client id"
    ): Pair<MockFlowFiber, StateAndEventProcessor.Response<Checkpoint>> {
        val startRPCFlowPayload = StartRPCFlow.newBuilder()
            .setClientId(clientId)
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
            addFlowFiber(this)
        }
        return fiber to onNext(state = null, event = Record(Schemas.Flow.FLOW_EVENT_TOPIC, key, event))
    }

    fun addFlowFiber(fiber: MockFlowFiber) {
        flowRunner.addFlowFiber(fiber)
    }
}

fun StateAndEventProcessor.Response<Checkpoint>.filterOutputFlowTopicRecords(): List<Record<FlowKey, FlowEvent>> {
    return responseEvents
        .filter { it.topic == Schemas.Flow.FLOW_EVENT_TOPIC }
        .map { record ->
            require(record.key is FlowKey) {
                "${Schemas.Flow.FLOW_EVENT_TOPIC} should only receive records with keys of type ${FlowKey::class.qualifiedName}"
            }
            require(record.value is FlowEvent) {
                "${Schemas.Flow.FLOW_EVENT_TOPIC} should only receive records with values of type ${FlowEvent::class.qualifiedName}"
            }
            uncheckedCast(record)
        }
}

fun StateAndEventProcessor.Response<Checkpoint>.filterOutputFlowTopicEvents(): List<FlowEvent> {
    return filterOutputFlowTopicRecords().mapNotNull { it.value }
}

fun StateAndEventProcessor.Response<Checkpoint>.filterOutputFlowTopicEventPayloads(): List<*> {
    return filterOutputFlowTopicEvents().map { it.payload }
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

    private var checkpoint: Checkpoint? = null
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
        val (mockFlowFiber, _) = processor.startFlow(flowId)
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
        return processor.onNext(
            state = checkpoint,
            event = Record(Schemas.Flow.FLOW_EVENT_TOPIC, event.flowKey, event)
        ).also { response ->
            checkpoint = response.updatedState
            outputFlowEvents.addAll(response.filterOutputFlowTopicEvents())
        }
    }

    override fun processAll(): List<StateAndEventProcessor.Response<Checkpoint>> {
        // Copy [inputs] and throw it away for code simplicity
        return inputFlowEvents.toList().map { processOne() }
    }

    private object ProcessLastOutputFlowEvent
}

class MockFlowFiber(val flowId: String = UUID.randomUUID().toString()) {

    private var requests = mutableListOf<FlowIORequest<*>>()

    fun queueSuspension(request: FlowIORequest<*>) {
        requests.add(
            when (request) {
                is FlowIORequest.FlowFinished -> request
                is FlowIORequest.FlowFailed -> request
                else -> FlowIORequest.FlowSuspended(ByteBuffer.wrap(byteArrayOf(0)), request)
            }
        )
    }

    fun repeatSuspension(request: FlowIORequest<*>, times: Int) {
        repeat(times) { queueSuspension(request) }
    }

    fun dequeueSuspension(): FlowIORequest<*> {
        check(requests.isNotEmpty()) { "The next ${FlowIORequest::class.java.simpleName} that the mocked flow returns must be set" }
        return requests.removeFirst()
    }

}