package net.corda.session.mapper.service.executor

import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.metadata
import net.corda.session.mapper.service.state.StateMetadataKeys.FLOW_MAPPER_STATUS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class ScheduledTaskHandlerTest {

    private val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
    private val window = 1000L
    private val states = listOf(
        createStateEntry("key1", clock.instant().minusMillis(window * 2), FlowMapperStateType.CLOSING),
        createStateEntry("key4", clock.instant().minusMillis(window * 3), FlowMapperStateType.CLOSING)
    ).toMap()

    @Test
    fun `when scheduled task handler generates new records, ID of each retrieved state is present in output events`() {
        val stateManager = mock<StateManager>()
        whenever(stateManager.findUpdatedBetweenWithMetadataFilter(any(), any())).thenReturn(states)
        val scheduledTaskHandler = ScheduledTaskHandler(
            stateManager,
            clock,
            window
        )
        val output = scheduledTaskHandler.process()
        val ids = output.flatMap { it.ids }
        assertThat(ids).contains("key1", "key4")
        verify(stateManager).findUpdatedBetweenWithMetadataFilter(
            IntervalFilter(Instant.MIN, clock.instant() - Duration.ofMillis(window)),
            MetadataFilter(FLOW_MAPPER_STATUS, Operation.Equals, FlowMapperStateType.CLOSING.toString())
        )
    }

    @Test
    fun `when batch size is set to one, a record per id is present in output events`() {
        val stateManager = mock<StateManager>()
        whenever(stateManager.findUpdatedBetweenWithMetadataFilter(any(), any())).thenReturn(states)
        val scheduledTaskHandler = ScheduledTaskHandler(
            stateManager,
            clock,
            window,
            1
        )
        val output = scheduledTaskHandler.process()
        assertThat(output.size).isEqualTo(2)
    }

    @Test
    fun `when the last updated time is far enough in the past, no records are returned`() {
        val stateManager = mock<StateManager>()
        whenever(stateManager.findUpdatedBetweenWithMetadataFilter(any(), any())).thenReturn(mapOf())
        val scheduledTaskHandler = ScheduledTaskHandler(
            stateManager,
            clock,
            window * 5
        )
        val output = scheduledTaskHandler.process()
        assertThat(output).isEmpty()
        verify(stateManager).findUpdatedBetweenWithMetadataFilter(
            IntervalFilter(Instant.MIN, clock.instant() - Duration.ofMillis(window * 5)),
            MetadataFilter(FLOW_MAPPER_STATUS, Operation.Equals, FlowMapperStateType.CLOSING.toString())
        )
    }

    private fun createStateEntry(
        key: String,
        lastUpdated: Instant,
        mapperState: FlowMapperStateType
    ): Pair<String, State> {
        val state = State(
            key,
            byteArrayOf(),
            metadata = metadata(FLOW_MAPPER_STATUS to mapperState.toString()),
            modifiedTime = lastUpdated
        )
        return Pair(key, state)
    }
}