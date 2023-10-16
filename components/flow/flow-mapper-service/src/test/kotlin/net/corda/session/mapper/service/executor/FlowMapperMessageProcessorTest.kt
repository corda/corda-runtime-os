package net.corda.session.mapper.service.executor

import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
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
import net.corda.libs.statemanager.api.Metadata
import net.corda.messaging.api.processor.StateAndEventProcessor.State
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig
import net.corda.session.mapper.service.state.StateMetadataKeys.FLOW_MAPPER_STATUS
import net.corda.test.flow.util.buildSessionEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
class FlowMapperMessageProcessorTest {

    private val flowMapperEventExecutor: FlowMapperEventExecutor = mock<FlowMapperEventExecutor>().apply {
        whenever(execute()).thenReturn(FlowMapperResult(null, emptyList()))
    }
    private val flowMapperEventExecutorFactory: FlowMapperEventExecutorFactory = mock<FlowMapperEventExecutorFactory>().apply {
        whenever(create(any(), any(), anyOrNull(), any(), any())).thenReturn(flowMapperEventExecutor)
    }
    private val config = SmartConfigImpl.empty().withValue(FlowConfig.SESSION_P2P_TTL, ConfigValueFactory.fromAnyRef(10000))
    private val flowMapperMessageProcessor = FlowMapperMessageProcessor(flowMapperEventExecutorFactory, config)

    private fun buildMapperState(status: FlowMapperStateType, metadata: Metadata = Metadata()) : State<FlowMapperState> {
        return State(
            FlowMapperState.newBuilder()
                .setStatus(status)
                .setFlowId("flowId")
                .setExpiryTime(Instant.now().toEpochMilli())
                .build(),
            metadata = metadata,
        )
    }

    private fun buildMapperEvent(payload: Any) : Record<String, FlowMapperEvent> {
        return Record("topic", "key", FlowMapperEvent.newBuilder()
            .setPayload(payload)
            .build()
        )
    }

    private fun buildSessionEvent(timestamp: Instant = Instant.now().plusSeconds(600) ) : SessionEvent {
        return buildSessionEvent(
            MessageDirection.INBOUND,
            "sessionId",
            null,
            SessionInit(),
            timestamp,
            contextSessionProps = null
        )
    }

    @Test
    fun `when state is null new session events are processed`() {
        flowMapperMessageProcessor.onNext(null, buildMapperEvent(buildSessionEvent()))
        verify(flowMapperEventExecutorFactory, times(1)).create(any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun `when state is OPEN new session events are processed`() {
        val metadata = Metadata(mapOf("foo" to "bar"))
        whenever(flowMapperEventExecutor.execute()).thenReturn(FlowMapperResult(FlowMapperState().apply {
            status = FlowMapperStateType.OPEN
        }, listOf()))
        val output = flowMapperMessageProcessor.onNext(
            buildMapperState(FlowMapperStateType.OPEN, metadata),buildMapperEvent(buildSessionEvent())
        )
        verify(flowMapperEventExecutorFactory, times(1)).create(any(), any(), anyOrNull(), any(), any())
        assertThat(output.updatedState?.metadata).isEqualTo(
            Metadata(metadata + mapOf(FLOW_MAPPER_STATUS to FlowMapperStateType.OPEN.toString()))
        )
    }

    @Test
    fun `when state is OPEN new cleanup events are processed`() {
        flowMapperMessageProcessor.onNext(buildMapperState(FlowMapperStateType.OPEN), buildMapperEvent(ScheduleCleanup()))
        verify(flowMapperEventExecutorFactory, times(1)).create(any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun `when state is OPEN expired session events are not processed`() {
        val metadata = Metadata(mapOf("foo" to "bar"))
        val output = flowMapperMessageProcessor.onNext(
            buildMapperState(FlowMapperStateType.OPEN, metadata = metadata),
            buildMapperEvent(buildSessionEvent(Instant.now().minusSeconds(100000))))
        verify(flowMapperEventExecutorFactory, times(0)).create(any(), any(), anyOrNull(), any(), any())
        assertThat(output.updatedState?.metadata).isEqualTo(metadata)
    }

    @Test
    fun `when state is CLOSING expired session events are not processed`() {
        flowMapperMessageProcessor.onNext(buildMapperState(FlowMapperStateType.CLOSING), buildMapperEvent(buildSessionEvent(Instant.now()
            .minusSeconds(100000))))
        verify(flowMapperEventExecutorFactory, times(0)).create(any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun `when state is ERROR expired session events are not processed`() {
        flowMapperMessageProcessor.onNext(buildMapperState(FlowMapperStateType.ERROR), buildMapperEvent(buildSessionEvent(Instant.now()
            .minusSeconds(100000))))
        verify(flowMapperEventExecutorFactory, times(0)).create(any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun `when state is CLOSING cleanup events are processed`() {
        flowMapperMessageProcessor.onNext(buildMapperState(FlowMapperStateType.CLOSING), buildMapperEvent(ExecuteCleanup()))
        verify(flowMapperEventExecutorFactory, times(1)).create(any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun `when state is CLOSING new session events are processed`() {
        flowMapperMessageProcessor.onNext(buildMapperState(FlowMapperStateType.CLOSING), buildMapperEvent(buildSessionEvent(Instant.now())))
        verify(flowMapperEventExecutorFactory, times(1)).create(any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun `when state is ERROR new session events are processed`() {
        flowMapperMessageProcessor.onNext(buildMapperState(FlowMapperStateType.ERROR), buildMapperEvent(buildSessionEvent(Instant.now())))
        verify(flowMapperEventExecutorFactory, times(1)).create(any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun `when input event has no value the existing state is returned`() {
        val metadata = Metadata(mapOf("foo" to "bar"))
        val state = buildMapperState(FlowMapperStateType.OPEN, metadata)
        val output = flowMapperMessageProcessor.onNext(state, Record("foo", "foo", null))
        verify(flowMapperEventExecutorFactory, times(0)).create(any(), any(), anyOrNull(), any(), any())
        assertThat(output.updatedState).isEqualTo(state)
    }

    @Test
    fun `when input metadata is null metadata is still set`() {
        whenever(flowMapperEventExecutor.execute()).thenReturn(FlowMapperResult(FlowMapperState().apply {
            status = FlowMapperStateType.OPEN
        }, listOf()))
        val output = flowMapperMessageProcessor.onNext(
            buildMapperState(FlowMapperStateType.OPEN),buildMapperEvent(buildSessionEvent())
        )
        verify(flowMapperEventExecutorFactory, times(1)).create(any(), any(), anyOrNull(), any(), any())
        assertThat(output.updatedState?.metadata).isEqualTo(
            Metadata(mapOf(FLOW_MAPPER_STATUS to FlowMapperStateType.OPEN.toString()))
        )
    }
}
