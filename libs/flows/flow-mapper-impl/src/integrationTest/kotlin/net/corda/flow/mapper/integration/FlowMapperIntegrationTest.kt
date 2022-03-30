package net.corda.flow.mapper.integration

import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.FlowStatusKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.mapper.factory.FlowMapperEventExecutorFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.schema.Schemas.Flow.Companion.FLOW_MAPPER_EVENT_TOPIC
import net.corda.test.flow.util.buildSessionEvent
import net.corda.v5.base.util.uncheckedCast
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.ByteBuffer
import java.time.Instant

@ExtendWith(ServiceExtension::class)
class FlowMapperIntegrationTest {

    private val identity = HoldingIdentity("x500", "grp1")
    private val startRPCFlow = StartFlow(
        FlowStartContext(
            FlowStatusKey("a", identity),
            FlowInitiatorType.RPC,
            "clientId",
            identity,
            "cpi id",
            identity,
            "className",
            Instant.MIN
        ), null
    )

    @InjectService(timeout = 4000)
    lateinit var executorFactory: FlowMapperEventExecutorFactory

    @Test
    fun `Send StartRPC`() {
        val flowMapperEvent = FlowMapperEvent(startRPCFlow)
        val inputKey = "key1"
        val result = onNext(null, Record(FLOW_MAPPER_EVENT_TOPIC, inputKey, flowMapperEvent))

        val state = result.updatedState
        val outputEvent = result.responseEvents.first()

        assertThat(outputEvent.key::class.java).isEqualTo(FlowKey::class.java)
        assertThat(state?.flowKey).isEqualTo(outputEvent.key)
        assertThat(outputEvent.value).isNotNull
        assertThat(outputEvent.value!!::class.java).isEqualTo(FlowEvent::class.java)
    }

    @Test
    fun `Send ScheduleCleanup`() {
        val scheduleCleanup = ScheduleCleanup(Long.MAX_VALUE)
        val flowMapperEvent = FlowMapperEvent(scheduleCleanup)
        val inputKey = "sessionId"
        val result =
            onNext(FlowMapperState(FlowKey(), null, FlowMapperStateType.OPEN), Record(FLOW_MAPPER_EVENT_TOPIC, inputKey, flowMapperEvent))

        val state = result.updatedState
        val outputEvent = result.responseEvents

        assertThat(outputEvent).isEmpty()
        assertThat(state?.status).isEqualTo(FlowMapperStateType.CLOSING)
        assertThat(state?.expiryTime).isNotNull
    }

    @Test
    fun `Send ExecuteCleanup`() {
        val executeCleanup = ExecuteCleanup()
        val flowMapperEvent = FlowMapperEvent(executeCleanup)
        val inputKey = "sessionId"
        val result =
            onNext(FlowMapperState(FlowKey(), null, FlowMapperStateType.OPEN), Record(FLOW_MAPPER_EVENT_TOPIC, inputKey, flowMapperEvent))

        val state = result.updatedState
        val outputEvent = result.responseEvents

        assertThat(outputEvent).isEmpty()
        assertThat(state).isNull()
    }

    @Test
    fun `Send SessionInit`() {
        val flowKey = FlowKey("flowId", HoldingIdentity("x500-1", "group"))
        val inputKey = "sessionId"
        val sessionInit = SessionInit(
            "flowName", "cpiId", flowKey, null
        )

        val sessionEvent = buildSessionEvent(MessageDirection.OUTBOUND, inputKey, 1, sessionInit)
        val flowMapperEvent = FlowMapperEvent(sessionEvent)
        val result = onNext(null, Record(FLOW_MAPPER_EVENT_TOPIC, inputKey, flowMapperEvent))

        val state = result.updatedState
        val outputEvent = result.responseEvents.first()

        assertThat(outputEvent.key).isEqualTo(inputKey)
        assertThat(state?.flowKey).isNotNull

        val outputEventPayload = outputEvent.value ?: fail("Payload was null")
        assertThat(outputEventPayload::class.java).isEqualTo(AppMessage::class.java)
    }

    @Test
    fun `Receive SessionInit`() {
        val inputKey = "sessionId-INITIATED"
        val sessionInit = SessionInit(
            "flowName",
            "cpiId",
            null,
            null
        )
        val sessionEvent = buildSessionEvent(MessageDirection.INBOUND, inputKey, 1, sessionInit)
        val flowMapperEvent = FlowMapperEvent(sessionEvent)
        val result = onNext(null, Record(FLOW_MAPPER_EVENT_TOPIC, inputKey, flowMapperEvent))

        val state = result.updatedState
        val outputEvent = result.responseEvents.first()

        assertThat(outputEvent.key::class.java).isEqualTo(FlowKey::class.java)
        assertThat(state?.flowKey).isNotNull

        val outputEventPayload = outputEvent.value ?: fail("Payload was null")
        val outputFlowEvent: FlowEvent = uncheckedCast(outputEventPayload)
        val outputSessionEvent: SessionEvent = uncheckedCast(outputFlowEvent.payload)
        assertThat(outputSessionEvent.payload::class.java).isEqualTo(SessionInit::class.java)
    }

    @Test
    fun `Send SessionData as initiator`() {
        val flowKey = FlowKey("flowId", HoldingIdentity("x500-1", "group"))
        val inputKey = "sessionId"
        val sessionEvent = buildSessionEvent(MessageDirection.OUTBOUND, inputKey, 3, SessionData(ByteBuffer.wrap("".toByteArray())))
        val flowMapperEvent = FlowMapperEvent(sessionEvent)
        val flowMapperState = FlowMapperState(flowKey, null, FlowMapperStateType.OPEN)
        val result = onNext(flowMapperState, Record(FLOW_MAPPER_EVENT_TOPIC, inputKey, flowMapperEvent))

        val outputEvent = result.responseEvents.first()
        assertThat(outputEvent.key).isEqualTo(inputKey)

        val outputEventPayload = outputEvent.value ?: fail("Payload was null")
        assertThat(outputEventPayload::class.java).isEqualTo(AppMessage::class.java)
    }

    @Test
    fun `Receive SessionData as initiator`() {
        val flowKey = FlowKey("flowId", HoldingIdentity("x500-2", "group"))
        val inputKey = "sessionId"
        val sessionEvent = buildSessionEvent(MessageDirection.INBOUND, inputKey, 3, SessionData(ByteBuffer.wrap("".toByteArray())))
        val flowMapperEvent = FlowMapperEvent(sessionEvent)
        val flowMapperState = FlowMapperState(flowKey, null, FlowMapperStateType.OPEN)
        val result = onNext(flowMapperState, Record(FLOW_MAPPER_EVENT_TOPIC, inputKey, flowMapperEvent))

        val outputEvent = result.responseEvents.first()
        assertThat(outputEvent.key).isEqualTo(flowKey)

        val outputEventPayload = outputEvent.value ?: fail("Payload was null")
        val outputFlowEvent: FlowEvent = uncheckedCast(outputEventPayload)
        assertThat(outputFlowEvent.payload::class.java).isEqualTo(SessionEvent::class.java)
    }

    @Test
    fun `Send SessionData as initiated`() {
        val flowKey = FlowKey("flowId", HoldingIdentity("x500-1", "group"))
        val inputKey = "sessionId-INITIATED"
        val sessionEvent = buildSessionEvent(MessageDirection.OUTBOUND, inputKey, 3, SessionData(ByteBuffer.wrap("".toByteArray())))
        val flowMapperEvent = FlowMapperEvent(sessionEvent)
        val flowMapperState = FlowMapperState(flowKey, null, FlowMapperStateType.OPEN)
        val result = onNext(flowMapperState, Record(FLOW_MAPPER_EVENT_TOPIC, inputKey, flowMapperEvent))

        val outputEvent = result.responseEvents.first()
        assertThat(outputEvent.key).isEqualTo(inputKey)

        val outputEventPayload = outputEvent.value ?: fail("Payload was null")
        assertThat(outputEventPayload::class.java).isEqualTo(AppMessage::class.java)
    }

    @Test
    fun `Receive SessionData as initiated`() {
        val flowKey = FlowKey("flowId", HoldingIdentity("x500-2", "group"))
        val inputKey = "sessionId-INITIATED"
        val sessionEvent = buildSessionEvent(MessageDirection.INBOUND, inputKey, 3, SessionData(ByteBuffer.wrap("".toByteArray())))
        val flowMapperEvent = FlowMapperEvent(sessionEvent)
        val flowMapperState = FlowMapperState(flowKey, null, FlowMapperStateType.OPEN)
        val result = onNext(flowMapperState, Record(FLOW_MAPPER_EVENT_TOPIC, inputKey, flowMapperEvent))

        val outputEvent = result.responseEvents.first()
        assertThat(outputEvent.key).isEqualTo(flowKey)

        val outputEventPayload = outputEvent.value ?: fail("Payload was null")
        val outputFlowEvent: FlowEvent = uncheckedCast(outputEventPayload)
        assertThat(outputFlowEvent.payload::class.java).isEqualTo(SessionEvent::class.java)
    }

    private fun onNext(
        state: FlowMapperState?,
        event: Record<String, FlowMapperEvent>
    ): StateAndEventProcessor.Response<FlowMapperState> {
        val executor = executorFactory.create(event.key, event.value!!, state)
        val result = executor.execute()
        return StateAndEventProcessor.Response(result.flowMapperState, result.outputEvents)
    }
}
