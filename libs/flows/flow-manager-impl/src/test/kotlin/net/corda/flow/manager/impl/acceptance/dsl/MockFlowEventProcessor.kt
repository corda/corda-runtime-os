package net.corda.flow.manager.impl.acceptance.dsl

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
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

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