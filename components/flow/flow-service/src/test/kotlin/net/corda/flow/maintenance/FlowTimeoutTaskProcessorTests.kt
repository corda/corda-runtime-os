package net.corda.flow.maintenance

import net.corda.data.flow.FlowTimeout
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.flow.state.impl.CheckpointMetadataKeys.STATE_META_SESSION_EXPIRY_KEY
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.constants.MessagingMetadataKeys
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class FlowTimeoutTaskProcessorTests {
    private val now = Instant.now()
    private val flowConfig = mock<SmartConfig>().apply {
        whenever(getLong(any())).thenReturn(10L)
    }

    private val idleState = State("idle", randomBytes(), 0)
    private val sessionTimeoutState =
        State(
            "sessionTimeout", randomBytes(), 0,
            Metadata(mapOf(STATE_META_SESSION_EXPIRY_KEY to now.minusSeconds(1).epochSecond.toInt()))
        )
    private val messagingLayerTimeoutState =
        State(
            "messagingLayerTimeout", randomBytes(), 0,
            Metadata(mapOf(MessagingMetadataKeys.PROCESSING_FAILURE to true))
        )
    private val stateManager = mock<StateManager> {
        on { updatedBetween(any()) } doReturn (mapOf(idleState.key to idleState))
        on { findByMetadataMatchingAny(any()) } doReturn (mapOf(
            sessionTimeoutState.key to sessionTimeoutState,
            messagingLayerTimeoutState.key to messagingLayerTimeoutState
        ))
    }
    private val record1 = Record<String, ScheduledTaskTrigger>(
        Schemas.ScheduledTask.SCHEDULED_TASK_NAME_SESSION_TIMEOUT,
        Schemas.ScheduledTask.SCHEDULED_TASK_NAME_SESSION_TIMEOUT,
        mock()
    )
    private val processor = FlowTimeoutTaskProcessor(stateManager, flowConfig) { now }

    @Test
    fun `when empty list do nothing`() {
        val output = processor.onNext(emptyList())
        assertThat(output).isEmpty()
        verify(stateManager, never()).findByMetadata(any())
    }

    @Test
    fun `when multiple scheduled tasks process only the first one`() {
        processor.onNext(listOf(record1, record1.copy(value = mock())))
        verify(stateManager, times(1)).findByMetadataMatchingAny(any())
    }

    @Test
    fun `ignore wrong scheduled tasks with wrong key`() {
        val output = processor.onNext(listOf(record1.copy(key = "foo")))
        assertThat(output).isEmpty()
        verify(stateManager, never()).findByMetadataMatchingAny(any())
    }

    @Test
    fun `when states are found return one clean up record per state`() {
        val output = processor.onNext(listOf(record1))
        assertThat(output).containsExactlyInAnyOrder(
            Record(
                Schemas.Flow.FLOW_TIMEOUT_TOPIC,
                idleState.key,
                FlowTimeout(idleState.key, now)
            ),
            Record(
                Schemas.Flow.FLOW_TIMEOUT_TOPIC,
                sessionTimeoutState.key,
                FlowTimeout(sessionTimeoutState.key, now)
            ),
            Record(
                Schemas.Flow.FLOW_TIMEOUT_TOPIC,
                messagingLayerTimeoutState.key,
                FlowTimeout(messagingLayerTimeoutState.key, now)
            )
        )
    }

    @Test
    fun `when no states are found return empty list of clean up records`() {
        whenever(stateManager.updatedBetween(any())).doReturn(emptyMap())
        whenever(stateManager.findByMetadataMatchingAny(any())).doReturn(emptyMap())

        val output = processor.onNext(listOf(record1))
        assertThat(output).isEmpty()
    }

    private fun randomBytes(): ByteArray {
        return (1..16).map { ('0'..'9').random() }.joinToString("").toByteArray()
    }
}
