package net.corda.flow.maintenance

import net.corda.data.flow.FlowTimeout
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.flow.state.impl.CheckpointMetadataKeys.STATE_META_SESSION_EXPIRY_KEY
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.STATE_TYPE
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
    private val checkpointMetadata = STATE_TYPE to Checkpoint::class.java.name
    private val flowConfig = mock<SmartConfig>().apply {
        whenever(getLong(any())).thenReturn(10L)
    }

    private val nonCheckpointState = State("not-a-checkpoint", randomBytes(), 0)
    private val idleState = State("idle", randomBytes(), 0, Metadata(mapOf(checkpointMetadata)))
    private val sessionTimeoutState =
        State(
            "sessionTimeout", randomBytes(), 0,
            Metadata(
                mapOf(
                    checkpointMetadata,
                    STATE_META_SESSION_EXPIRY_KEY to now.minusSeconds(1).epochSecond.toInt()
                )
            )
        )
    private val messagingLayerTimeoutState =
        State(
            "messagingLayerTimeout", randomBytes(), 0,
            Metadata(
                mapOf(
                    checkpointMetadata,
                    MessagingMetadataKeys.PROCESSING_FAILURE to true
                )
            )
        )
    private val stateManager = mock<StateManager> {
        on { findByMetadataMatchingAny(any()) } doReturn (mapOf(
            nonCheckpointState.key to nonCheckpointState,
            sessionTimeoutState.key to sessionTimeoutState,
            messagingLayerTimeoutState.key to messagingLayerTimeoutState
        ))
        on { findUpdatedBetweenWithMetadataMatchingAll(any(), any()) } doReturn (mapOf(
            idleState.key to idleState
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
        verify(stateManager, times(1)).findUpdatedBetweenWithMetadataMatchingAll(any(), any())
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
                FlowTimeout(idleState.key, FlowTimeoutTaskProcessor.MAX_IDLE_TIME_ERROR_MESSAGE, now)
            ),
            Record(
                Schemas.Flow.FLOW_TIMEOUT_TOPIC,
                sessionTimeoutState.key,
                FlowTimeout(sessionTimeoutState.key, FlowTimeoutTaskProcessor.SESSION_EXPIRED_ERROR_MESSAGE, now)
            ),
            Record(
                Schemas.Flow.FLOW_TIMEOUT_TOPIC,
                messagingLayerTimeoutState.key,
                FlowTimeout(messagingLayerTimeoutState.key, FlowTimeoutTaskProcessor.PROCESS_FAILURE_ERROR_MESSAGE, now)
            )
        )
    }

    @Test
    fun `when no states are found return empty list of clean up records`() {
        whenever(
            stateManager.findByMetadataMatchingAny(any())
        ).doReturn(mapOf(nonCheckpointState.key to nonCheckpointState))
        whenever(
            stateManager.findUpdatedBetweenWithMetadataMatchingAll(any(), any())
        ).doReturn(emptyMap())

        val output = processor.onNext(listOf(record1))
        assertThat(output).isEmpty()
    }

    private fun randomBytes(): ByteArray {
        return (1..16)
            .map { ('0'..'9').random() }
            .joinToString("")
            .toByteArray()
    }
}
