package net.corda.flow.mapper.impl.executor

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.mapper.FlowMapperMetaData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StartRPCFlowExecutorTest {

    @Test
    fun testStartRPCFlowExecutor() {
        val meta = FlowMapperMetaData(FlowMapperEvent(), "", "FlowEventTopic", HoldingIdentity(), StartRPCFlow(), null, null, Long
            .MAX_VALUE)
        val result = StartRPCFlowExecutor(meta).execute()
        val state = result.flowMapperState
        assertThat(state?.flowKey).isNotNull
        assertThat(state?.status).isEqualTo(FlowMapperStateType.OPEN)
        assertThat(state?.expiryTime).isNull()

        assertThat(result.outputEvents.size).isEqualTo(1)
        val outputEvent = result.outputEvents.first()
        assertThat(outputEvent.value!!::class).isEqualTo(FlowEvent::class)
        assertThat(outputEvent.key::class).isEqualTo(FlowKey::class)
        assertThat(outputEvent.topic).isEqualTo("FlowEventTopic")
    }

    @Test
    fun testStartRPCFlowExecutorNonNullState() {
        val inputState = FlowMapperState()
        val meta = FlowMapperMetaData(FlowMapperEvent(), "", "FlowEventTopic", HoldingIdentity(), StartRPCFlow(), inputState, null, Long
            .MAX_VALUE)
        val result = StartRPCFlowExecutor(meta).execute()
        assertThat(inputState).isEqualTo(result.flowMapperState)
        assertThat(result.outputEvents).isEmpty()
    }
}