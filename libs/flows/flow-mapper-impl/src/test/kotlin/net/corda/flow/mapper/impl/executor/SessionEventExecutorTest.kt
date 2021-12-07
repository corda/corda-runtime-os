package net.corda.flow.mapper.impl.executor

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.MessageDirection
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.mapper.FlowMapperMetaData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionEventExecutorTest {

    @Test
    fun testSessionEventExecutorSessionInitOutbound() {
        val holdingIdentity = HoldingIdentity()
        val flowKey = FlowKey()
        val payload = SessionEvent(1, 1, SessionInit("", "", FlowKey(), holdingIdentity))
        val meta = FlowMapperMetaData(
            FlowMapperEvent(), "sessionId", "outputTopic", holdingIdentity, payload, null,
            MessageDirection.OUTBOUND, null
        )
        val result = SessionEventExecutor(meta).execute()
        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNotNull
        assertThat(state?.flowKey).isEqualTo(flowKey)
        assertThat(state?.status).isEqualTo(FlowMapperStateType.OPEN)
        assertThat(state?.expiryTime).isEqualTo(null)

        assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        assertThat(outboundEvent.topic).isEqualTo("outputTopic")
        assertThat(outboundEvent.key).isEqualTo("sessionId-INITIATED")
        assertThat(outboundEvent.value!!::class).isEqualTo(FlowMapperEvent::class)
    }

    @Test
    fun testSessionEventExecutorSessionInitInbound() {
        val holdingIdentity = HoldingIdentity()
        val payload = SessionEvent(1, 1, SessionInit("", "", null, holdingIdentity))
        val meta = FlowMapperMetaData(
            FlowMapperEvent(), "sessionId", "outputTopic", holdingIdentity, payload, null,
            MessageDirection.INBOUND, null
        )
        val result = SessionEventExecutor(meta).execute()
        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNotNull
        assertThat(state?.flowKey).isNotNull
        assertThat(state?.status).isEqualTo(FlowMapperStateType.OPEN)
        assertThat(state?.expiryTime).isEqualTo(null)

        assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        assertThat(outboundEvent.topic).isEqualTo("outputTopic")
        assertThat(outboundEvent.key::class).isEqualTo(FlowKey::class)
        assertThat(outboundEvent.value!!::class).isEqualTo(FlowEvent::class)
    }

    @Test
    fun testSessionEventExecutorSessionDataOutbound() {
        val payload = SessionEvent(1, 1, SessionData(null))
        val meta = FlowMapperMetaData(
            FlowMapperEvent(), "sessionId", "outputTopic", null, payload, FlowMapperState(),
            MessageDirection.OUTBOUND, null
        )
        val result = SessionEventExecutor(meta).execute()
        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNotNull

        assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        assertThat(outboundEvent.topic).isEqualTo("outputTopic")
        assertThat(outboundEvent.key).isEqualTo("sessionId-INITIATED")
        assertThat(outboundEvent.value!!::class).isEqualTo(FlowMapperEvent::class)
    }

    @Test
    fun testSessionEventExecutorSessionDataInbound() {
        val flowKey = FlowKey()
        val payload = SessionEvent(1, 1, SessionData(null))
        val meta = FlowMapperMetaData(
            FlowMapperEvent(), "sessionId-INITIATED", "outputTopic", null, payload, FlowMapperState(flowKey, null, FlowMapperStateType
                .OPEN),
            MessageDirection.INBOUND, null
        )
        val result = SessionEventExecutor(meta).execute()
        val state = result.flowMapperState
        val outboundEvents = result.outputEvents

        assertThat(state).isNotNull

        assertThat(outboundEvents.size).isEqualTo(1)
        val outboundEvent = outboundEvents.first()
        assertThat(outboundEvent.topic).isEqualTo("outputTopic")
        assertThat(outboundEvent.key).isEqualTo(flowKey)
        assertThat(outboundEvent.value!!::class).isEqualTo(FlowEvent::class)
    }
}
