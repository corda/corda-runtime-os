package net.corda.flow.acceptance.dsl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.acceptance.getBasicFlowStartContext
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.factory.impl.FlowEventPipelineFactoryImpl
import net.corda.flow.pipeline.factory.impl.RecordFactoryImpl
import net.corda.flow.pipeline.handlers.events.SessionEventHandler
import net.corda.flow.pipeline.handlers.events.StartFlowEventHandler
import net.corda.flow.pipeline.handlers.events.WakeupEventHandler
import net.corda.flow.pipeline.handlers.requests.FlowFailedRequestHandler
import net.corda.flow.pipeline.handlers.requests.FlowFinishedRequestHandler
import net.corda.flow.pipeline.handlers.requests.ForceCheckpointRequestHandler
import net.corda.flow.pipeline.handlers.requests.SleepRequestHandler
import net.corda.flow.pipeline.handlers.requests.SubFlowFailedRequestHandler
import net.corda.flow.pipeline.handlers.requests.SubFlowFinishedRequestHandler
import net.corda.flow.pipeline.handlers.requests.sessions.CloseSessionsRequestHandler
import net.corda.flow.pipeline.handlers.requests.sessions.GetFlowInfoRequestHandler
import net.corda.flow.pipeline.handlers.requests.sessions.InitiateFlowRequestHandler
import net.corda.flow.pipeline.handlers.requests.sessions.ReceiveRequestHandler
import net.corda.flow.pipeline.handlers.requests.sessions.SendAndReceiveRequestHandler
import net.corda.flow.pipeline.handlers.requests.sessions.SendRequestHandler
import net.corda.flow.pipeline.handlers.requests.sessions.WaitForSessionConfirmationsRequestHandler
import net.corda.flow.pipeline.handlers.waiting.StartFlowWaitingForHandler
import net.corda.flow.pipeline.handlers.waiting.WakeupWaitingForHandler
import net.corda.flow.pipeline.handlers.waiting.sessions.SessionConfirmationWaitingForHandler
import net.corda.flow.pipeline.handlers.waiting.sessions.SessionDataWaitingForHandler
import net.corda.flow.pipeline.handlers.waiting.sessions.SessionInitWaitingForHandler
import net.corda.flow.pipeline.impl.FlowEventProcessorImpl
import net.corda.flow.pipeline.impl.FlowGlobalPostProcessorImpl
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sessions.FlowSessionManagerImpl
import net.corda.flow.state.impl.FlowCheckpointFactoryImpl
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.schema.Schemas
import net.corda.schema.configuration.FlowConfig
import net.corda.session.manager.impl.SessionManagerImpl
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

fun flowEventDSL(dsl: FlowEventDSL.() -> Unit) {
    FlowEventDSL().run(dsl)
}

class FlowEventDSL {

    private val flowRunner = MockFlowRunner()
    private val processor =
        FlowEventProcessorImpl(
            FlowEventPipelineFactoryImpl(
                flowRunner,
                flowGlobalPostProcessor,
                FlowCheckpointFactoryImpl(),
                flowEventHandlers,
                flowWaitingForHandlers,
                flowRequestHandlers
            ),
            testSmartConfig
        )

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

    fun startedFlowFiber(
        flowId: String = UUID.randomUUID().toString(),
        fiber: MockFlowFiber.() -> Unit
    ): MockFlowFiber {
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
        val flowId = event.flowId
        return processor.onNext(
            state = checkpoints[flowId],
            event = Record(Schemas.Flow.FLOW_EVENT_TOPIC, event.flowId, event)
        ).also { updateDSLStateWithEventResponse(flowId, it) }
    }

    fun processAll(): List<StateAndEventProcessor.Response<Checkpoint>> {
        check(inputFlowEvents.isNotEmpty()) { "No input flow events have been setup" }
        return inputFlowEvents.toList().map { processOne() }
    }

    private fun startFlow(flowId: String): Pair<MockFlowFiber, StateAndEventProcessor.Response<Checkpoint>> {
        val startRPCFlowPayload = StartFlow.newBuilder()
            .setStartContext(getBasicFlowStartContext())
            .setFlowStartArgs(" { \"json\": \"args\" }")
            .build()

        val key = flowId

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

private val sessionManager = SessionManagerImpl()
private val recordFactory = RecordFactoryImpl()

private val flowSessionManager = FlowSessionManagerImpl(sessionManager)

private val flowGlobalPostProcessor = FlowGlobalPostProcessorImpl(sessionManager, recordFactory)

private val sandboxGroupContext = mock<SandboxGroupContext>()

private val flowSandboxService = mock<FlowSandboxService>().apply {
    whenever(get(any())).thenReturn(sandboxGroupContext)
}

// Must be updated when new flow event handlers are added
private val flowEventHandlers = listOf(
    SessionEventHandler(flowSandboxService, sessionManager),
    StartFlowEventHandler(),
    WakeupEventHandler(),
)

private val flowWaitingForHandlers = listOf(
    StartFlowWaitingForHandler(),
    WakeupWaitingForHandler(),
    SessionConfirmationWaitingForHandler(flowSessionManager),
    SessionDataWaitingForHandler(flowSessionManager),
    SessionInitWaitingForHandler(sessionManager)
)

// Must be updated when new flow request handlers are added
private val flowRequestHandlers = listOf(
    CloseSessionsRequestHandler(flowSessionManager, recordFactory),
    FlowFailedRequestHandler(mock(), recordFactory),
    FlowFinishedRequestHandler(mock(), recordFactory),
    ForceCheckpointRequestHandler(recordFactory),
    GetFlowInfoRequestHandler(),
    InitiateFlowRequestHandler(flowSessionManager),
    ReceiveRequestHandler(flowSessionManager, recordFactory),
    SendAndReceiveRequestHandler(flowSessionManager, recordFactory),
    SendRequestHandler(flowSessionManager, recordFactory),
    SleepRequestHandler(),
    SubFlowFailedRequestHandler(),
    SubFlowFinishedRequestHandler(flowSessionManager, recordFactory),
    WaitForSessionConfirmationsRequestHandler()
)

private val testConfig = ConfigFactory.empty()
    .withValue(FlowConfig.SESSION_MESSAGE_RESEND_WINDOW, ConfigValueFactory.fromAnyRef(500000L))
    .withValue(FlowConfig.SESSION_HEARTBEAT_TIMEOUT_WINDOW, ConfigValueFactory.fromAnyRef(500000L))
private val configFactory = SmartConfigFactory.create(testConfig)
private val testSmartConfig = configFactory.create(testConfig)