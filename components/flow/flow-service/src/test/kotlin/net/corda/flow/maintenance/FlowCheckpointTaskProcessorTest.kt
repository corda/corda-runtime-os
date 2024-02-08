package net.corda.flow.maintenance

import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.FlowCheckpointTermination
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.flow.state.impl.CheckpointMetadataKeys.STATE_META_CHECKPOINT_TERMINATED_KEY
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.STATE_TYPE
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.metadata
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.FlowConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class FlowCheckpointTaskProcessorTest {

    private val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
    private val window = 1000L
    private val config = SmartConfigImpl.empty().withValue(FlowConfig.PROCESSING_FLOW_CHECKPOINT_CLEANUP_TIME, ConfigValueFactory
        .fromAnyRef(window))
    private val terminatedStates = listOf(
        createStateEntry("key1", clock.instant().minusMillis(window * 2)),
        createStateEntry("key4", clock.instant().minusMillis(window * 3))
    ).toMap()

    private val inputEvent = Record(
        Schemas.ScheduledTask.SCHEDULED_TASK_TOPIC_FLOW_PROCESSOR,
        "foo",
        ScheduledTaskTrigger(Schemas.ScheduledTask.SCHEDULED_TASK_NAME_FLOW_CHECKPOINT_TERMINATION, clock.instant())
    )

    @Test
    fun `when scheduled task handler generates new records, ID of each retrieved state is present in output events`() {
        val stateManager = mock<StateManager>()
        whenever(stateManager.findUpdatedBetweenWithMetadataMatchingAny(any(), any())).thenReturn(terminatedStates)
        val scheduledTaskProcessor = FlowCheckpointTerminationTaskProcessor(
            stateManager,
            config,
            clock
        )
        val output = scheduledTaskProcessor.onNext(listOf(inputEvent))
        val ids = output.flatMap { (it.value as FlowCheckpointTermination).checkpointStateKeys }
        assertThat(ids).contains("key1", "key4")
        verify(stateManager).findUpdatedBetweenWithMetadataMatchingAny(
            IntervalFilter(Instant.EPOCH, clock.instant() - Duration.ofMillis(window)),
            listOf(
                MetadataFilter(STATE_TYPE, Operation.Equals, Checkpoint::class.java.name),
                MetadataFilter(STATE_META_CHECKPOINT_TERMINATED_KEY, Operation.Equals, true),
            )
        )
    }

    @Test
    fun `when batch size is set to one, a record per id is present in output events`() {
        val stateManager = mock<StateManager>()
        whenever(stateManager.findUpdatedBetweenWithMetadataMatchingAny(any(), any())).thenReturn(terminatedStates)
        val scheduledTaskProcessor = FlowCheckpointTerminationTaskProcessor(
            stateManager,
            config,
            clock,
            1
        )
        val output = scheduledTaskProcessor.onNext(listOf(inputEvent))
        assertThat(output.size).isEqualTo(2)
    }

    @Test
    fun `when the last updated time is far enough in the past, no records are returned`() {
        val stateManager = mock<StateManager>()
        whenever(stateManager.findUpdatedBetweenWithMetadataMatchingAny(any(), any())).thenReturn(mapOf())
        val scheduledTaskProcessor = FlowCheckpointTerminationTaskProcessor(
            stateManager,
            config.withValue(FlowConfig.PROCESSING_FLOW_CHECKPOINT_CLEANUP_TIME, ConfigValueFactory
                .fromAnyRef(window*5)),
            clock,
            1
        )
        val output = scheduledTaskProcessor.onNext(listOf(inputEvent))
        assertThat(output).isEmpty()
        verify(stateManager).findUpdatedBetweenWithMetadataMatchingAny(
            IntervalFilter(Instant.EPOCH, clock.instant() - Duration.ofMillis(window * 5)),
            listOf(
                MetadataFilter(STATE_TYPE, Operation.Equals, Checkpoint::class.java.name),
                MetadataFilter(STATE_META_CHECKPOINT_TERMINATED_KEY, Operation.Equals, true),
            )
        )
    }

    @Test
    fun `when the input record does not have the correct task name, no processing is attempted`() {
        val stateManager = mock<StateManager>()
        val scheduledTaskProcessor = FlowCheckpointTerminationTaskProcessor(
            stateManager,
            config,
            clock
        )
        val input = Record(
            Schemas.ScheduledTask.SCHEDULED_TASK_TOPIC_MAPPER_PROCESSOR,
            "foo",
            ScheduledTaskTrigger("wrong-name", clock.instant())
        )
        val output = scheduledTaskProcessor.onNext(listOf(input))
        assertThat(output).isEmpty()
        verify(stateManager, never()).findUpdatedBetweenWithMetadataMatchingAny(any(), any())
    }

    private fun createStateEntry(
        key: String,
        lastUpdated: Instant
    ): Pair<String, State> {
        val metadata = metadata(STATE_META_CHECKPOINT_TERMINATED_KEY to true)
        val state = State(
            key,
            byteArrayOf(),
            metadata = metadata,
            modifiedTime = lastUpdated
        )
        return Pair(key, state)
    }
}
