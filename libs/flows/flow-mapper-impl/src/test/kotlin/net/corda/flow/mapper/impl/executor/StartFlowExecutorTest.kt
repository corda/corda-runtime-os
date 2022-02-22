package net.corda.flow.mapper.impl.executor

import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.FlowStatusKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.data.virtualnode.VirtualNodeInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class StartFlowExecutorTest {
    private val eventKey = "key"
    private val outputTopic = "topic"
    private val identity = HoldingIdentity("x500", "grp1")
    private val startRPCFlow = StartFlow(
        FlowStartContext(
            FlowStatusKey("a", identity),
            FlowInitiatorType.RPC,
            "clientId",
            VirtualNodeInfo(identity, null),
            "className",
            Instant.MIN
        ), null
    )

    @Test
    fun testStartRPCFlowExecutor() {

        val result = StartFlowExecutor(eventKey, outputTopic, startRPCFlow, null).execute()
        val state = result.flowMapperState
        assertThat(state?.flowKey).isNotNull
        assertThat(state?.status).isEqualTo(FlowMapperStateType.OPEN)
        assertThat(state?.expiryTime).isNull()

        assertThat(result.outputEvents.size).isEqualTo(1)
        val outputEvent = result.outputEvents.first()
        assertThat(outputEvent.value!!::class).isEqualTo(FlowEvent::class)
        assertThat(outputEvent.key::class).isEqualTo(FlowKey::class)
        assertThat(outputEvent.topic).isEqualTo(outputTopic)
    }

    @Test
    fun testStartRPCFlowExecutorNonNullState() {
        val inputState = FlowMapperState()
        val result = StartFlowExecutor(eventKey, outputTopic, startRPCFlow, inputState).execute()
        assertThat(inputState).isEqualTo(result.flowMapperState)
        assertThat(result.outputEvents).isEmpty()
    }
}