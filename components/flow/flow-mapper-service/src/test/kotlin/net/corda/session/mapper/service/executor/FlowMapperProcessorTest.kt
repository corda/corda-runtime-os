package net.corda.session.mapper.service.executor

import com.typesafe.config.ConfigValueFactory
import java.time.Instant
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.flow.mapper.factory.FlowMapperEventExecutorFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig
import net.corda.test.flow.util.buildSessionEvent
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowMapperProcessorTest {

    private val flowMapperEventExecutor: FlowMapperEventExecutor = mock<FlowMapperEventExecutor>().apply {
        whenever(execute()).thenReturn(FlowMapperResult(null, emptyList()))
    }
    private val flowMapperEventExecutorFactory: FlowMapperEventExecutorFactory = mock<FlowMapperEventExecutorFactory>().apply {
        whenever(create(any(), any(), anyOrNull(), any(), any())).thenReturn(flowMapperEventExecutor)
    }
    private val config = SmartConfigImpl.empty().withValue(FlowConfig.SESSION_P2P_TTL, ConfigValueFactory.fromAnyRef(10000))
    private val flowMapperMessageProcessor = FlowMapperMessageProcessor(flowMapperEventExecutorFactory, config)

    private fun buildMapperState(status: FlowMapperStateType) : FlowMapperState {
        return FlowMapperState.newBuilder()
            .setStatus(status)
            .setFlowId("flowId")
            .setExpiryTime(Instant.now().toEpochMilli())
            .build()
    }

    private fun buildMapperEvent(payload: Any) : Record<String, FlowMapperEvent> {
        return Record("topic", "key", FlowMapperEvent.newBuilder()
            .setPayload(payload)
            .build()
        )
    }

    private fun buildSessionEvent(timestamp: Instant = Instant.now().plusSeconds(600) ) : FlowEvent {
        return FlowEvent.newBuilder()
            .setPayload(buildSessionEvent(MessageDirection.INBOUND, "sessionId", null, SessionInit(), 0, listOf(), timestamp))
            .setFlowId("flowId")
            .build()
    }

    @Test
    fun `when state is closing new session events are not processed`() {
        flowMapperMessageProcessor.onNext(buildMapperState(FlowMapperStateType.CLOSING), buildMapperEvent(buildSessionEvent()))
        verify(flowMapperEventExecutorFactory, times(0)).create(any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun `when state is null new flow events are processed`() {
        flowMapperMessageProcessor.onNext(null, buildMapperEvent(buildSessionEvent()))
        verify(flowMapperEventExecutorFactory, times(1)).create(any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun `when state is OPEN new flow events are processed`() {
        flowMapperMessageProcessor.onNext(buildMapperState(FlowMapperStateType.OPEN), buildMapperEvent(buildSessionEvent()))
        verify(flowMapperEventExecutorFactory, times(1)).create(any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun `when state is OPEN new cleanup events are processed`() {
        flowMapperMessageProcessor.onNext(buildMapperState(FlowMapperStateType.OPEN), buildMapperEvent(ScheduleCleanup()))
        verify(flowMapperEventExecutorFactory, times(1)).create(any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun `when state is OPEN expired session events are processed`() {
        flowMapperMessageProcessor.onNext(buildMapperState(FlowMapperStateType.OPEN), buildMapperEvent(buildSessionEvent(Instant.now()
            .minusSeconds(100000))))
        verify(flowMapperEventExecutorFactory, times(0)).create(any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun `when state is CLOSING new flow events are not processed`() {
        flowMapperMessageProcessor.onNext(buildMapperState(FlowMapperStateType.CLOSING), buildMapperEvent(buildSessionEvent()))
        verify(flowMapperEventExecutorFactory, times(0)).create(any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun `when state is CLOSING cleanup events are processed`() {
        flowMapperMessageProcessor.onNext(buildMapperState(FlowMapperStateType.CLOSING), buildMapperEvent(ExecuteCleanup()))
        verify(flowMapperEventExecutorFactory, times(1)).create(any(), any(), anyOrNull(), any(), any())
    }
}
