package net.corda.messaging.subscription.consumer.wrapper

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.TOPIC_PREFIX
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.constants.SubscriptionType
import net.corda.messaging.createResolvedSubscriptionConfig
import net.corda.messaging.subscription.consumer.StateAndEventConsumerImpl
import net.corda.messaging.subscription.consumer.StateAndEventPartitionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.inOrder
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.util.concurrent.CountDownLatch

class StateAndEventConsumerImplTest {

    private companion object {
        const val TOPIC = "topic"
        private val config = createResolvedSubscriptionConfig(SubscriptionType.STATE_AND_EVENT)
    }

    @Test
    fun testClose() {
        val (stateAndEventListener, eventConsumer, stateConsumer, partitions) = setupMocks()
        val partitionId = partitions.first().partition
        val partitionState =
            StateAndEventPartitionState<String, String>(
                mutableMapOf(partitionId to mutableMapOf()),
                mutableMapOf(partitionId to Long.MAX_VALUE)
            )
        val consumer =
            StateAndEventConsumerImpl(
                config,
                eventConsumer,
                stateConsumer,
                partitionState,
                stateAndEventListener
            )
        consumer.close()

        verify(eventConsumer, times(1)).close()
        verify(stateConsumer, times(1)).close()
    }

    @Test
    fun testGetValue() {
        val (stateAndEventListener, eventConsumer, stateConsumer, partitions) = setupMocks()
        val partitionId = partitions.first().partition
        val partitionState = StateAndEventPartitionState(
            mutableMapOf(
                partitionId to mutableMapOf(
                    "key1" to Pair(
                        Long.MIN_VALUE,
                        "value1"
                    )
                )
            ), mutableMapOf(partitionId to Long.MAX_VALUE)
        )
        val consumer =
            StateAndEventConsumerImpl(
                config,
                eventConsumer,
                stateConsumer,
                partitionState,
                stateAndEventListener
            )
        val valueKey1 = consumer.getInMemoryStateValue("key1")
        assertThat(valueKey1).isEqualTo("value1")
        val valueKey2 = consumer.getInMemoryStateValue("key2")
        assertThat(valueKey2).isNull()
    }

    @Test
    fun testOnProcessorStateUpdated() {
        val (stateAndEventListener, eventConsumer, stateConsumer, partitions) = setupMocks()
        val partitionId = partitions.first().partition
        val partitionState = StateAndEventPartitionState(
            mutableMapOf(partitionId to mutableMapOf("key1" to Pair(Long.MIN_VALUE, "value1"))),
            mutableMapOf(partitionId to Long.MAX_VALUE)
        )
        val consumer =
            StateAndEventConsumerImpl(
                config,
                eventConsumer,
                stateConsumer,
                partitionState,
                stateAndEventListener
            )

        consumer.updateInMemoryStatePostCommit(
            mutableMapOf(partitionId to mutableMapOf("key1" to null, "key2" to "value2")),
            Clock.systemUTC()
        )

        val currentStates = partitionState.currentStates
        assertThat(currentStates[partitionId]?.get("key1")).isNull()
        assertThat(currentStates[partitionId]?.get("key2")).isNotNull

        verify(stateAndEventListener, times(1)).onPostCommit(any())
    }

    @Test
    fun testUpdateStates() {
        val (stateAndEventListener, eventConsumer, stateConsumer, partitions) = setupMocks()
        val partitionId = partitions.first().partition
        val partitionState = StateAndEventPartitionState(
            mutableMapOf(partitionId to mutableMapOf("key1" to Pair(Long.MIN_VALUE, "value1"))),
            mutableMapOf(partitionId to Long.MAX_VALUE)
        )
        val consumer =
            StateAndEventConsumerImpl(
                config,
                eventConsumer,
                stateConsumer,
                partitionState,
                stateAndEventListener
            )

        val record = CordaConsumerRecord(TOPIC_PREFIX + TOPIC, partitionId, 0, "key1", "value1", 0)
        whenever(stateConsumer.poll(any())).thenReturn(listOf(record))

        consumer.pollAndUpdateStates(true)

        verify(stateConsumer, times(1)).assignment()
        verify(stateConsumer, times(1)).poll(any())
        verify(stateConsumer, times(1)).poll(any())
        verify(stateAndEventListener, times(1)).onPartitionSynced(mapOf("key1" to "value1"))

        // partitionsToSync should have had the synced partition removed
        assertThat(partitionState.partitionsToSync.size).isEqualTo(0)
    }

    @Test
    fun testUpdateStatesNoSync() {
        val (stateAndEventListener, eventConsumer, stateConsumer, partitions) = setupMocks()
        val partitionId = partitions.first().partition
        val partitionState = StateAndEventPartitionState(
            mutableMapOf(partitionId to mutableMapOf("key1" to Pair(Long.MIN_VALUE, "value1"))),
            mutableMapOf(partitionId to Long.MAX_VALUE)
        )

        val record = CordaConsumerRecord(TOPIC_PREFIX + TOPIC, partitionId, 0, "key1", "value1", 0)
        whenever(stateConsumer.poll(any())).thenReturn(listOf(record))

        val consumer = StateAndEventConsumerImpl(
            config,
            eventConsumer,
            stateConsumer,
            partitionState,
            stateAndEventListener
        )

        consumer.pollAndUpdateStates(false)

        verify(stateConsumer, times(1)).assignment()
        verify(stateConsumer, times(1)).poll(any())
        verify(stateConsumer, times(1)).poll(any())
        verify(stateAndEventListener, times(0)).onPartitionSynced(any())

        // When false is passed to pollAndUpdateStates to indicate not to try and process whether partitions are synced
        // or not, partitionsToSync should remain untouched, even though we return a record from the poll above which
        // corresponds to a state which would otherwise have marked the partition as synced.
        assertThat(partitionState.partitionsToSync.size).isEqualTo(1)
        assertThat(partitionState.partitionsToSync[partitionId]).isEqualTo(Long.MAX_VALUE)
    }

    @Test
    fun testWaitForFunctionToFinish() {
        val (stateAndEventListener, eventConsumer, stateConsumer, partitions) = setupMocks()
        val partitionId = partitions.first().partition
        val partitionState = StateAndEventPartitionState(
            mutableMapOf(partitionId to mutableMapOf("key1" to Pair(Long.MIN_VALUE, "value1"))),
            mutableMapOf(partitionId to Long.MAX_VALUE)
        )
        val consumer = StateAndEventConsumerImpl(
            config,
            eventConsumer,
            stateConsumer,
            partitionState,
            stateAndEventListener
        )
        val latch = CountDownLatch(1)
        consumer.waitForFunctionToFinish({
            while (latch.count > 0) {
                Thread.sleep(10)
            }
        }, 200L, "test ")
        latch.countDown()

        verify(eventConsumer, times(1)).assignment()
        verify(eventConsumer, times(1)).paused()
        verify(eventConsumer, times(1)).pause(any())
        verify(eventConsumer, times(1)).resume(any())
        verify(stateConsumer, atLeast(1)).poll(any())
        verify(stateAndEventListener, times(0)).onPartitionSynced(any())
    }

    @Test
    fun testResetPollPosition() {
        val (stateAndEventListener, eventConsumer, stateConsumer, _) = setupMocks()
        val consumer = StateAndEventConsumerImpl(
            config, eventConsumer, stateConsumer, StateAndEventPartitionState
                (mutableMapOf(), mutableMapOf()), stateAndEventListener
        )

        val assignedTopicPartitions = setOf(CordaTopicPartition(TOPIC, 0), CordaTopicPartition(TOPIC, 1))
        val pausedTopicPartitions = setOf(CordaTopicPartition(TOPIC, 1))
        whenever(eventConsumer.assignment()).thenReturn(assignedTopicPartitions)
        whenever(eventConsumer.paused()).thenReturn(pausedTopicPartitions)

        consumer.resetPollInterval()

        val eventConsumerOrder = inOrder(eventConsumer)

        verify(eventConsumer, times(1)).assignment()
        verify(eventConsumer, times(1)).paused()

        eventConsumerOrder.verify(eventConsumer, times(1))
            .pause(argThat { contains(CordaTopicPartition(TOPIC, 0)) && size == 1 })
        eventConsumerOrder.verify(eventConsumer, times(1)).poll(any())
        eventConsumerOrder.verify(eventConsumer, times(1))
            .resume(argThat { contains(CordaTopicPartition(TOPIC, 0)) && size == 1 })

        val stateConsumerOrder = inOrder(stateConsumer)

        verify(stateConsumer, times(1)).assignment()
        verify(stateConsumer, times(1)).paused()
        stateConsumerOrder.verify(stateConsumer, times(1))
            .pause(argThat { contains(CordaTopicPartition(TOPIC, 0)) && size == 1 })
        stateConsumerOrder.verify(stateConsumer, times(1)).poll(any())
        stateConsumerOrder.verify(stateConsumer, times(1))
            .resume(argThat { contains(CordaTopicPartition(TOPIC, 0)) && size == 1 })
    }

    @Test
    fun `test repartition during poll returns false`() {
        val (stateAndEventListener, eventConsumer, stateConsumer, _) = setupMocks()
        val consumer = StateAndEventConsumerImpl(
            config, eventConsumer, stateConsumer, StateAndEventPartitionState
                (mutableMapOf(), mutableMapOf(), true), stateAndEventListener
        )

        assertThat(consumer.resetPollInterval()).isFalse
    }

    private fun setupMocks(): Mocks {
        val listener: StateAndEventListener<String, String> = mock()
        val eventConsumer: CordaConsumer<String, String> = mock()
        val stateConsumer: CordaConsumer<String, String> = mock()

        val topicPartitions = setOf(CordaTopicPartition(TOPIC, 0))

        val state = CordaConsumerRecord(TOPIC_PREFIX + TOPIC, 0, 0, "key", "state5", 0)

        doAnswer { topicPartitions }.whenever(stateConsumer).assignment()
        doAnswer { listOf(state) }.whenever(stateConsumer).poll(any())
        doAnswer { Long.MAX_VALUE }.whenever(stateConsumer).position(any())

        return Mocks(listener, eventConsumer, stateConsumer, topicPartitions)
    }

    data class Mocks(
        val stateAndEventListener: StateAndEventListener<String, String>,
        val eventConsumer: CordaConsumer<String, String>,
        val stateConsumer: CordaConsumer<String, String>,
        val partitions: Set<CordaTopicPartition>
    )
}
