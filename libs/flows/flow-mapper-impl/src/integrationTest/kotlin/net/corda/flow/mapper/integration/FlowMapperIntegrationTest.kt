package net.corda.flow.mapper.integration

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.MessageDirection
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.mapper.FlowMapperTopics
import net.corda.flow.mapper.factory.FlowMapperEventExecutorFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.uncheckedCast
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.time.Instant

@ExtendWith(ServiceExtension::class)
class FlowMapperIntegrationTest {

    private val P2P_OUT = "P2POut"
    private val FLOW_MAPPER_TOPIC = "FlowMapperTopic"
    private val FLOW_EVENT_TOPIC = "FlowEventTopic"
    private val flowMapperTopics = FlowMapperTopics(P2P_OUT, FLOW_MAPPER_TOPIC, FLOW_EVENT_TOPIC)


    @InjectService(timeout = 4000)
    lateinit var executorFactory: FlowMapperEventExecutorFactory

    @Test
    fun sendStartRPC() {
        val startRPCFlow = StartRPCFlow("clientId", "cpiId", "flowName", HoldingIdentity("x500", "group"), Instant.now(), "args")
        val flowMapperEvent = FlowMapperEvent(null, startRPCFlow)
        val inputKey = "key1"
        val result = onNext(null, Record(FLOW_MAPPER_TOPIC, inputKey, flowMapperEvent))

        val state = result.updatedState
        val outputEvent = result.responseEvents.first()

        assertThat(outputEvent.key::class.java).isEqualTo(FlowKey::class.java)
        assertThat(state?.flowKey).isEqualTo(outputEvent.key)
        assertThat(outputEvent.value).isNotNull
        assertThat(outputEvent.value!!::class.java).isEqualTo(FlowEvent::class.java)
    }

    @Test
    fun sendScheduleCleanup() {
        val scheduleCleanup = ScheduleCleanup(Long.MAX_VALUE)
        val flowMapperEvent = FlowMapperEvent(null, scheduleCleanup)
        val inputKey = "sessionId"
        val result = onNext(FlowMapperState(FlowKey(), null, FlowMapperStateType.OPEN), Record(FLOW_MAPPER_TOPIC, inputKey, flowMapperEvent))

        val state = result.updatedState
        val outputEvent = result.responseEvents

        assertThat(outputEvent).isEmpty()
        assertThat(state?.status).isEqualTo(FlowMapperStateType.CLOSING)
        assertThat(state?.expiryTime).isNotNull
    }

    @Test
    fun sendExecuteCleanup() {
        val executeCleanup = ExecuteCleanup()
        val flowMapperEvent = FlowMapperEvent(null, executeCleanup)
        val inputKey = "sessionId"
        val result = onNext(FlowMapperState(FlowKey(), null, FlowMapperStateType.OPEN), Record(FLOW_MAPPER_TOPIC, inputKey, flowMapperEvent))

        val state = result.updatedState
        val outputEvent = result.responseEvents

        assertThat(outputEvent).isEmpty()
        assertThat(state).isNull()
    }

    @Test
    fun sendSessionInit() {
        val flowKey = FlowKey("flowId", HoldingIdentity("x500-1", "group"))
        val sessionInit = SessionEvent(1L, 1, SessionInit("flowName", "cpiId", flowKey, HoldingIdentity("x500-2", "group")))
        val flowMapperEvent = FlowMapperEvent(MessageDirection.OUTBOUND, sessionInit)
        val inputKey = "sessionId"
        val result = onNext(null, Record(FLOW_MAPPER_TOPIC, inputKey, flowMapperEvent))

        val state = result.updatedState
        val outputEvent = result.responseEvents.first()

        assertThat(outputEvent.key).isEqualTo("$inputKey-INITIATED")
        assertThat(state?.flowKey).isNotNull

        val outputEventPayload = outputEvent.value ?: fail("Payload was null")
        val outputMapperEvent: FlowMapperEvent = uncheckedCast(outputEventPayload)
        val outputSessionEvent: SessionEvent = uncheckedCast(outputMapperEvent.payload)
        assertThat(outputSessionEvent.payload::class.java).isEqualTo(SessionInit::class.java)
    }

    @Test
    fun receiveSessionInit() {
        val sessionInit = SessionEvent(1L, 1, SessionInit("flowName", "cpiId", null, HoldingIdentity("x500-2", "group")))
        val flowMapperEvent = FlowMapperEvent(MessageDirection.INBOUND, sessionInit)
        val inputKey = "sessionId-INITIATED"
        val result = onNext(null, Record(FLOW_MAPPER_TOPIC, inputKey, flowMapperEvent))

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
    fun sendSessionDataAsInitiator() {
        val flowKey = FlowKey("flowId", HoldingIdentity("x500-1", "group"))
        val sessionData = SessionEvent(1L, 3, SessionData(null))
        val flowMapperEvent = FlowMapperEvent(MessageDirection.OUTBOUND, sessionData)
        val flowMapperState = FlowMapperState(flowKey, null, FlowMapperStateType.OPEN)
        val inputKey = "sessionId"
        val result = onNext(flowMapperState, Record(FLOW_MAPPER_TOPIC, inputKey, flowMapperEvent))

        val outputEvent = result.responseEvents.first()
        assertThat(outputEvent.key).isEqualTo("$inputKey-INITIATED")

        val outputEventPayload = outputEvent.value ?: fail("Payload was null")
        val outputMapperEvent: FlowMapperEvent = uncheckedCast(outputEventPayload)
        assertThat(outputMapperEvent.payload::class.java).isEqualTo(SessionEvent::class.java)
    }

    @Test
    fun receiveSessionDataAsInitiator() {
        val flowKey = FlowKey("flowId", HoldingIdentity("x500-2", "group"))
        val sessionData = SessionEvent(1L, 3, SessionData(null))
        val flowMapperEvent = FlowMapperEvent(MessageDirection.INBOUND, sessionData)
        val flowMapperState = FlowMapperState(flowKey, null, FlowMapperStateType.OPEN)
        val inputKey = "sessionId"
        val result = onNext(flowMapperState, Record(FLOW_MAPPER_TOPIC, inputKey, flowMapperEvent))

        val outputEvent = result.responseEvents.first()
        assertThat(outputEvent.key).isEqualTo(flowKey)

        val outputEventPayload = outputEvent.value ?: fail("Payload was null")
        val outputFlowEvent: FlowEvent = uncheckedCast(outputEventPayload)
        assertThat(outputFlowEvent.payload::class.java).isEqualTo(SessionEvent::class.java)
    }

    @Test
    fun sendSessionDataAsInitiated() {
        val flowKey = FlowKey("flowId", HoldingIdentity("x500-1", "group"))
        val sessionData = SessionEvent(1L, 3, SessionData(null))
        val flowMapperEvent = FlowMapperEvent(MessageDirection.OUTBOUND, sessionData)
        val flowMapperState = FlowMapperState(flowKey, null, FlowMapperStateType.OPEN)
        val inputKey = "sessionId-INITIATED"
        val result = onNext(flowMapperState, Record(FLOW_MAPPER_TOPIC, inputKey, flowMapperEvent))

        val outputEvent = result.responseEvents.first()
        assertThat(outputEvent.key).isEqualTo("sessionId")

        val outputEventPayload = outputEvent.value ?: fail("Payload was null")
        val outputMapperEvent: FlowMapperEvent = uncheckedCast(outputEventPayload)
        assertThat(outputMapperEvent.payload::class.java).isEqualTo(SessionEvent::class.java)
    }

    @Test
    fun receiveSessionDataAsInitiated() {
        val flowKey = FlowKey("flowId", HoldingIdentity("x500-2", "group"))
        val sessionData = SessionEvent(1L, 3, SessionData(null))
        val flowMapperEvent = FlowMapperEvent(MessageDirection.INBOUND, sessionData)
        val flowMapperState = FlowMapperState(flowKey, null, FlowMapperStateType.OPEN)
        val inputKey = "sessionId-INITIATED"
        val result = onNext(flowMapperState, Record(FLOW_MAPPER_TOPIC, inputKey, flowMapperEvent))

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
        val executor = executorFactory.create(event.key, event.value!!, state, flowMapperTopics)
        val result = executor.execute()
        return StateAndEventProcessor.Response(result.flowMapperState, result.outputEvents)
    }
}
