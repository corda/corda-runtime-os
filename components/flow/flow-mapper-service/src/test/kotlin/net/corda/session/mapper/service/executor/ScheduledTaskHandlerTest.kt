package net.corda.session.mapper.service.executor

import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.SingleKeyFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.metadata
import net.corda.session.mapper.service.state.StateMetadataKeys.FLOW_MAPPER_STATUS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class ScheduledTaskHandlerTest {

    private val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
    private val window = 1000L
    private val states = listOf(
        createStateEntry("key1", clock.instant().minusMillis(window * 2), FlowMapperStateType.CLOSING),
        createStateEntry("key2", clock.instant(), FlowMapperStateType.CLOSING),
        createStateEntry("key3", clock.instant().minusMillis(window * 2), FlowMapperStateType.OPEN),
        createStateEntry("key4", clock.instant().minusMillis(window * 3), FlowMapperStateType.CLOSING),
        createStateEntry("key5", clock.instant().minusMillis(window * 2), null)
    ).toMap()

    @Test
    fun `when scheduled task handler generates new records, ID of each retrieved state is present in output events`() {
        val scheduledTaskHandler = ScheduledTaskHandler(
            StateManagerImpl(),
            clock,
            window
        )
        val output = scheduledTaskHandler.process()
        val ids = output.flatMap { it.ids }
        assertThat(ids).contains("key1", "key4")
    }

    @Test
    fun `when batch size is set to one, a record per id is present in output events`() {
        val scheduledTaskHandler = ScheduledTaskHandler(
            StateManagerImpl(),
            clock,
            window,
            1
        )
        val output = scheduledTaskHandler.process()
        assertThat(output.size).isEqualTo(2)
    }

    @Test
    fun `when the last updated time is far enough in the past, no records are returned`() {
        val scheduledTaskHandler = ScheduledTaskHandler(
            StateManagerImpl(),
            clock,
            window * 5
        )
        val output = scheduledTaskHandler.process()
        assertThat(output).isEmpty()
    }

    private inner class StateManagerImpl : StateManager {
        override fun create(states: Collection<State>): Map<String, Exception> {
            TODO("Not yet implemented")
        }

        override fun get(keys: Collection<String>): Map<String, State> {
            TODO("Not yet implemented")
        }

        override fun update(states: Collection<State>): Map<String, State> {
            TODO("Not yet implemented")
        }

        override fun delete(states: Collection<State>): Map<String, State> {
            TODO("Not yet implemented")
        }

        override fun updatedBetween(intervalFilter: IntervalFilter): Map<String, State> {
            TODO("Not yet implemented")
        }

        override fun find(singleKeyFilter: SingleKeyFilter): Map<String, State> {
            TODO("Not yet implemented")
        }

        override fun findUpdatedBetweenWithMetadataFilter(
            intervalFilter: IntervalFilter,
            singleKeyFilter: SingleKeyFilter
        ): Map<String, State> {
            val inInterval = { state: State ->
                state.modifiedTime.isAfter(intervalFilter.start) && state.modifiedTime.isBefore(intervalFilter.finish)
            }
            // For the metadata filter assume it uses equals.
            if (singleKeyFilter.operation != Operation.Equals) {
                throw IllegalArgumentException("Must use equals")
            }
            val keyFilterMatches = { state: State ->
                state.metadata[singleKeyFilter.key] == singleKeyFilter.value
            }
            return states.filter { inInterval(it.value) && keyFilterMatches(it.value) }
        }

        override fun close() {
            TODO("Not yet implemented")
        }
    }

    private fun createStateEntry(
        key: String,
        lastUpdated: Instant,
        mapperState: FlowMapperStateType?
    ): Pair<String, State> {
        val metadata = metadata()
        mapperState?.let {
            metadata.put(FLOW_MAPPER_STATUS, it.toString())
        }
        val state = State(
            key,
            byteArrayOf(),
            metadata = metadata,
            modifiedTime = lastUpdated
        )
        return Pair(key, state)
    }
}