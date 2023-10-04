package net.corda.flow.maintenance

import net.corda.data.flow.FlowTimeout
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.flow.maintenance.SessionTimeoutTaskProcessor.Companion.STATE_META_SESSION_EXPIRY_KEY
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class SessionTimeoutTaskProcessorTests {
    private val now = Instant.now()
    private val state1 =
        State(
            "foo",
            randomBytes(),
            0,
            Metadata(mapOf(STATE_META_SESSION_EXPIRY_KEY to now.minusSeconds(1).epochSecond)))
    private val states = mapOf(
        state1.key to state1,
    )
    private val stateManager = mock<StateManager> {
        on { findByMetadata(any()) } doReturn (states)
    }
    private val record1 = Record<String, ScheduledTaskTrigger>(
        Schemas.ScheduledTask.SCHEDULED_TASK_NAME_SESSION_TIMEOUT,
        Schemas.ScheduledTask.SCHEDULED_TASK_NAME_SESSION_TIMEOUT,
        mock())

    @Test
    fun `when empty list do nothing`() {
        val processor = SessionTimeoutTaskProcessor(stateManager) { now }
        val output = processor.onNext(emptyList())
        assertThat(output).isEmpty()
        verify(stateManager, never()).findByMetadata(any())
    }

    @Test
    fun `when multiple in list do only process one`() {
        val processor = SessionTimeoutTaskProcessor(stateManager) { now }
        processor.onNext(listOf(record1, record1.copy(value = mock())))
        verify(stateManager, Times(1)).findByMetadata(any())
    }

    @Test
    fun `filter out wrong key`() {
        val processor = SessionTimeoutTaskProcessor(stateManager) { now }
        val output = processor.onNext(listOf(record1.copy(key = "foo")))
        assertThat(output).isEmpty()
        verify(stateManager, never()).findByMetadata(any())
    }

    @Test
    fun `when state found return`() {
        val processor = SessionTimeoutTaskProcessor(stateManager) { now }
        val output = processor.onNext(listOf(record1))
        assertThat(output).containsExactly(
            Record(
                Schemas.Flow.FLOW_TIMEOUT_TOPIC,
                state1.key,
                FlowTimeout(
                    state1.key,
                    Instant.ofEpochSecond(state1.metadata[STATE_META_SESSION_EXPIRY_KEY] as Long))
            )
        )
    }

    @Test
    fun `when no states found return empty`() {
        whenever(stateManager.findByMetadata(any())).doReturn(emptyMap())
        val processor = SessionTimeoutTaskProcessor(stateManager) { now }
        val output = processor.onNext(listOf(record1))
        // TODO - better assertion when integrated
        assertThat(output).isEmpty()
    }

    private fun randomBytes(): ByteArray {
        return (1..16).map { ('0'..'9').random() }.joinToString("").toByteArray()
    }
}
