package net.corda.session.mapper.service.executor

import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.metadata
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.session.mapper.service.state.StateMetadataKeys.FLOW_MAPPER_STATUS
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

class ScheduledTaskProcessorTest {

    private val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
    private val window = 1000L
    private val closingStates = listOf(
        createStateEntry("key1", clock.instant().minusMillis(window * 2), FlowMapperStateType.CLOSING.toString()),
        createStateEntry("key4", clock.instant().minusMillis(window * 3), FlowMapperStateType.CLOSING.toString())
    ).toMap()

    private val errorStates = listOf(
        createStateEntry("key2", clock.instant().minusMillis(window * 2), FlowMapperStateType.CLOSING.toString()),
        createStateEntry("key5", clock.instant().minusMillis(window * 3), FlowMapperStateType.CLOSING.toString())
    ).toMap()
    private val inputEvent = Record(
        Schemas.ScheduledTask.SCHEDULED_TASK_TOPIC_MAPPER_PROCESSOR,
        "foo",
        ScheduledTaskTrigger(Schemas.ScheduledTask.SCHEDULED_TASK_NAME_MAPPER_CLEANUP, clock.instant())
    )

    @Test
    fun `when scheduled task handler generates new records, ID of each retrieved state is present in output events`() {
        val stateManager = mock<StateManager>()
        whenever(stateManager.findUpdatedBetweenWithMetadataMatchingAny(any(), any())).thenReturn(closingStates+errorStates)
        val scheduledTaskProcessor = ScheduledTaskProcessor(
            stateManager,
            clock,
            window
        )
        val output = scheduledTaskProcessor.onNext(listOf(inputEvent))
        val ids = output.flatMap { (it.value as ExecuteCleanup).ids }
        assertThat(ids).contains("key1", "key4")
        verify(stateManager).findUpdatedBetweenWithMetadataMatchingAny(
            IntervalFilter(Instant.EPOCH, clock.instant() - Duration.ofMillis(window)),
            listOf(
                MetadataFilter(FLOW_MAPPER_STATUS, Operation.Equals, FlowMapperStateType.ERROR.toString()),
                MetadataFilter(FLOW_MAPPER_STATUS, Operation.Equals, FlowMapperStateType.CLOSING.toString()),
            )
        )
    }

    @Test
    fun `when batch size is set to one, a record per id is present in output events`() {
        val stateManager = mock<StateManager>()
        whenever(stateManager.findUpdatedBetweenWithMetadataMatchingAny(any(), any())).thenReturn(closingStates)
        val scheduledTaskProcessor = ScheduledTaskProcessor(
            stateManager,
            clock,
            window,
            1
        )
        val output = scheduledTaskProcessor.onNext(listOf(inputEvent))
        assertThat(output.size).isEqualTo(2)
    }

    @Test
    fun `when the last updated time is far enough in the past, no records are returned`() {
        val stateManager = mock<StateManager>()
        whenever(stateManager.findUpdatedBetweenWithMetadataMatchingAny(any(), any())).thenReturn(mapOf())
        val scheduledTaskProcessor = ScheduledTaskProcessor(
            stateManager,
            clock,
            window * 5
        )
        val output = scheduledTaskProcessor.onNext(listOf(inputEvent))
        assertThat(output).isEmpty()
        verify(stateManager).findUpdatedBetweenWithMetadataMatchingAny(
            IntervalFilter(Instant.EPOCH, clock.instant() - Duration.ofMillis(window * 5)),
            listOf(
                MetadataFilter(FLOW_MAPPER_STATUS, Operation.Equals, FlowMapperStateType.ERROR.toString()),
                MetadataFilter(FLOW_MAPPER_STATUS, Operation.Equals, FlowMapperStateType.CLOSING.toString()),
            )
        )
    }

    @Test
    fun `when the input record does not have the correct task name, no processing is attempted`() {
        val stateManager = mock<StateManager>()
        val scheduledTaskProcessor = ScheduledTaskProcessor(
            stateManager,
            clock,
            window
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
        lastUpdated: Instant,
        status:String
    ): Pair<String, State> {
        val state = State(
            key,
            byteArrayOf(),
            metadata = metadata(FLOW_MAPPER_STATUS to status),
            modifiedTime = lastUpdated
        )
        return Pair(key, state)
    }
}
