package net.corda.session.mapper.service.executor

import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.messaging.api.publisher.Publisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FlowMapperListenerTest {

    private val eventTopic = "eventTopic"

    @Test
    fun testOnPartitionsLost() {
        val publisher: Publisher = mock()
        val scheduledKeys = listOf("1", "2", "3")
        val states = scheduledKeys.associateBy({ it }, { FlowMapperState(null, null, null) })
        val scheduledTaskState = generateScheduledTaskState(publisher, scheduledKeys)
        assertThat(scheduledTaskState.tasks.size).isEqualTo(3)

        FlowMapperListener(scheduledTaskState, eventTopic, Clock.systemUTC()).onPartitionLost(states)

        assertThat(scheduledTaskState.tasks.size).isEqualTo(0)
    }

    @Test
    fun testOnPartitionsAssignedExpiredStates() {
        val publisher: Publisher = mock()
        val clock: Clock = mock()
        val scheduledKeys = listOf("1", "2", "3")
        whenever(clock.millis()).thenReturn(1001)
        val states = scheduledKeys.associateBy({ it }, { FlowMapperState(null, 1000, FlowMapperStateType
            .CLOSING) })
        val scheduledTaskState = generateScheduledTaskState(publisher, emptyList())
        assertThat(scheduledTaskState.tasks.size).isEqualTo(0)

        FlowMapperListener(scheduledTaskState, eventTopic, clock).onPartitionSynced(states)

        verify(publisher, times(3)).publish(any())
        assertThat(scheduledTaskState.tasks.size).isEqualTo(0)
    }

    @Test
    fun testOnPartitionsAssignedClosingStates() {
        val publisher: Publisher = mock()
        val clock: Clock = mock()
        val scheduledKeys = listOf("1", "2", "3")
        whenever(clock.millis()).thenReturn(999)
        val states = scheduledKeys.associateBy({ it }, { FlowMapperState(null, 100000, FlowMapperStateType
            .CLOSING) })
        val scheduledTaskState = generateScheduledTaskState(publisher, emptyList())
        assertThat(scheduledTaskState.tasks.size).isEqualTo(0)

        FlowMapperListener(scheduledTaskState, eventTopic, clock).onPartitionSynced(states)

        verify(publisher, times(0)).publish(any())
        assertThat(scheduledTaskState.tasks.size).isEqualTo(3)
        scheduledTaskState.close()
        assertThat(scheduledTaskState.tasks.size).isEqualTo(0)
    }


    @Test
    fun testOnPartitionsCommitted() {
        val publisher: Publisher = mock()
        val clock: Clock = mock()
        val scheduledKeys = listOf("1", "2", "3")
        val states = scheduledKeys.associateBy({ it }, { FlowMapperState(null, 100000, FlowMapperStateType
            .CLOSING) })
        val scheduledTaskState = generateScheduledTaskState(publisher, emptyList())
        assertThat(scheduledTaskState.tasks.size).isEqualTo(0)

        FlowMapperListener(scheduledTaskState, eventTopic, clock).onPostCommit(states)

        verify(publisher, times(0)).publish(any())
        assertThat(scheduledTaskState.tasks.size).isEqualTo(3)
        scheduledTaskState.close()
        assertThat(scheduledTaskState.tasks.size).isEqualTo(0)
    }

    private fun generateScheduledTaskState(publisher: Publisher, scheduledKeys: List<String>): ScheduledTaskState {
        val executorService = Executors.newSingleThreadScheduledExecutor()

        return ScheduledTaskState(
            Executors.newSingleThreadScheduledExecutor(),
            publisher,
            scheduledKeys.associateBy({ it }, { executorService.schedule({ }, 100000, TimeUnit.MILLISECONDS) }).toMutableMap()
        )
    }
}