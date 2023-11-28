package net.corda.messaging.subscription.consumer

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messaging.TOPIC_PREFIX
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.constants.SubscriptionType
import net.corda.messaging.createResolvedSubscriptionConfig
import net.corda.schema.Schemas.getStateAndEventStateTopic
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.inOrder
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Duration
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
                mutableMapOf(partitionId to mutableMapOf())
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
            )
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
            mutableMapOf(partitionId to mutableMapOf("key1" to Pair(Long.MIN_VALUE, "value1")))
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
            mutableMapOf(partitionId to mutableMapOf("key1" to Pair(Long.MIN_VALUE, "value1")))
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
        val statePartitions = partitions.map { CordaTopicPartition(getStateAndEventStateTopic(it.topic), it.partition) }
        whenever(stateConsumer.beginningOffsets(statePartitions))
            .thenReturn(statePartitions.associateWith { 0L })
        whenever(stateConsumer.endOffsets(statePartitions))
            .thenReturn(statePartitions.associateWith { 1L })
        whenever(stateConsumer.position(any()))
            .thenReturn(1L)

        consumer.onPartitionsAssigned(partitions)
        consumer.pollAndUpdateStates(true)

        verify(stateConsumer, times(4)).assignment()
        verify(stateConsumer, times(1)).poll(any())
        verify(stateConsumer, times(1)).poll(any())
        verify(stateAndEventListener, times(1)).onPartitionSynced(mapOf("key1" to "value1"))
    }

    @Test
    fun testUpdateStatesNoSync() {
        val (stateAndEventListener, eventConsumer, stateConsumer, partitions) = setupMocks()
        val partitionId = partitions.first().partition
        val partitionState = StateAndEventPartitionState(
            mutableMapOf(partitionId to mutableMapOf("key1" to Pair(Long.MIN_VALUE, "value1")))
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
    }

    @Test
    fun `pollAndUpdateStates should not update in memory states for partitions not being synced`() {
        val (stateAndEventListener, eventConsumer, stateConsumer, partitions) = setupMocks()
        val partitionId = partitions.first().partition
        val key = "key1"
        val originalState = Pair(Long.MIN_VALUE, "value0")
        val partitionState = StateAndEventPartitionState(
            mutableMapOf(partitionId to mutableMapOf(key to originalState))
        )

        val record = CordaConsumerRecord(TOPIC_PREFIX + TOPIC, partitionId, 0, key, "value1", 0)
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
        assertThat(partitionState.currentStates[partitionId]!!["key1"]!!).isEqualTo(originalState)
    }

    @Test
    fun testWaitForFunctionToFinish() {
        val (stateAndEventListener, eventConsumer, stateConsumer, partitions) = setupMocks()
        val partitionId = partitions.first().partition
        val partitionState = StateAndEventPartitionState(
            mutableMapOf(partitionId to mutableMapOf("key1" to Pair(Long.MIN_VALUE, "value1")))
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

        verify(eventConsumer, times(2)).assignment()
        verify(eventConsumer, times(1)).pause(any())
        verify(eventConsumer, times(1)).resume(any())
        verify(stateConsumer, atLeast(1)).poll(any())
        verify(stateAndEventListener, times(0)).onPartitionSynced(any())
    }

    @Test
    fun testResetPollPosition() {
        val (stateAndEventListener, eventConsumer, stateConsumer, _) = setupMocks()

        val pausedEventTopicPartitions = setOf(CordaTopicPartition(TOPIC, 0))
        val assignedEventTopicPartitions =
            setOf(CordaTopicPartition(TOPIC, 0), CordaTopicPartition(TOPIC, 1), CordaTopicPartition(TOPIC, 2))
        val assignedEventTopicPartitionsAfterRebalance =
            setOf(CordaTopicPartition(TOPIC, 0), CordaTopicPartition(TOPIC, 1))
        whenever(eventConsumer.paused())
            .thenReturn(pausedEventTopicPartitions)
        whenever(eventConsumer.assignment())
            .thenReturn(assignedEventTopicPartitions)
            .thenReturn(assignedEventTopicPartitionsAfterRebalance)

        val pausedStateTopicPartitions = setOf(CordaTopicPartition(getStateAndEventStateTopic(TOPIC), 0))
        val assignedStateTopicPartitions = assignedEventTopicPartitions.map {
            CordaTopicPartition(getStateAndEventStateTopic(it.topic), it.partition)
        }.toSet()
        val assignedStateTopicPartitionsAfterRebalance = assignedEventTopicPartitionsAfterRebalance.map {
            CordaTopicPartition(getStateAndEventStateTopic(it.topic), it.partition)
        }.toSet()
        whenever(stateConsumer.paused())
            .thenReturn(pausedStateTopicPartitions)
        whenever(stateConsumer.assignment())
            .thenReturn(assignedStateTopicPartitions)
            .thenReturn(assignedStateTopicPartitions)
            .thenReturn(assignedStateTopicPartitions)
            .thenReturn(assignedStateTopicPartitionsAfterRebalance)
        whenever(stateConsumer.beginningOffsets(any()))
            .thenReturn(assignedStateTopicPartitions.associateWith { 0L })
        whenever(stateConsumer.endOffsets(any()))
            .thenReturn(
                mapOf(
                    CordaTopicPartition(getStateAndEventStateTopic(TOPIC), 0) to 1L,
                    CordaTopicPartition(getStateAndEventStateTopic(TOPIC), 1) to 0L,
                    CordaTopicPartition(getStateAndEventStateTopic(TOPIC), 2) to 0L
                )
            )

        val consumer = StateAndEventConsumerImpl(
            config,
            eventConsumer,
            stateConsumer,
            StateAndEventPartitionState
            (mutableMapOf()),
            stateAndEventListener
        )
        consumer.onPartitionsAssigned(assignedEventTopicPartitions)
        consumer.resetPollInterval()

        val eventConsumerOrder = inOrder(eventConsumer)
        eventConsumerOrder.verify(eventConsumer, times(1)).assignment()
        eventConsumerOrder.verify(eventConsumer, times(1)).paused()
        eventConsumerOrder.verify(eventConsumer, times(1))
            .pause(assignedEventTopicPartitions - pausedEventTopicPartitions)
        eventConsumerOrder.verify(eventConsumer, times(1)).poll(any())
        eventConsumerOrder.verify(eventConsumer, times(1)).assignment()
        eventConsumerOrder.verify(eventConsumer, times(1))
            .resume(assignedEventTopicPartitionsAfterRebalance - pausedEventTopicPartitions)

        val stateConsumerOrder = inOrder(stateConsumer)
        stateConsumerOrder.verify(stateConsumer, times(1)).assignment()
        stateConsumerOrder.verify(stateConsumer, times(1)).paused()
        stateConsumerOrder.verify(stateConsumer, times(1))
            .pause(assignedStateTopicPartitions - pausedStateTopicPartitions)
        stateConsumerOrder.verify(stateConsumer, times(1)).poll(any())
        stateConsumerOrder.verify(stateConsumer, times(1)).assignment()
        stateConsumerOrder.verify(stateConsumer, times(1))
            .resume(assignedStateTopicPartitionsAfterRebalance - pausedStateTopicPartitions)
    }

    @Test
    fun `repartition during reset poll interval throws exception and resumes correct partitions`() {
        val (stateAndEventListener, eventConsumer, stateConsumer, _) = setupMocks()
        val pausedEventTopicPartitions = setOf(CordaTopicPartition(TOPIC, 0), CordaTopicPartition(TOPIC, 1))
        val assignedEventTopicPartitions = setOf(
            CordaTopicPartition(TOPIC, 0),
            CordaTopicPartition(TOPIC, 1),
            CordaTopicPartition(TOPIC, 2),
            CordaTopicPartition(TOPIC, 3)
        )
        val assignedEventTopicPartitionsAfterRebalance = setOf(
            CordaTopicPartition(TOPIC, 0),
            CordaTopicPartition(TOPIC, 1),
            CordaTopicPartition(TOPIC, 3),
            CordaTopicPartition(TOPIC, 4)
        )

        whenever(eventConsumer.paused())
            .thenReturn(pausedEventTopicPartitions)
        whenever(eventConsumer.assignment())
            .thenReturn(assignedEventTopicPartitions)
            .thenReturn(assignedEventTopicPartitionsAfterRebalance)

        val stateAndEventPartitionState =
            StateAndEventPartitionState<String, String>(mutableMapOf(), false)
        val consumer = StateAndEventConsumerImpl(
            config,
            eventConsumer,
            stateConsumer,
            stateAndEventPartitionState,
            stateAndEventListener
        )

        doAnswer {
            stateAndEventPartitionState.dirty = true
            emptyList<String>()
        }.whenever(eventConsumer).poll(any())

        assertThatThrownBy {
            consumer.resetPollInterval()
        }.isInstanceOf(StateAndEventConsumer.RebalanceInProgressException::class.java)

        val eventConsumerOrder = inOrder(eventConsumer)
        eventConsumerOrder.verify(eventConsumer, times(1)).assignment()
        eventConsumerOrder.verify(eventConsumer, times(1)).paused()
        eventConsumerOrder.verify(eventConsumer, times(1))
            .pause(assignedEventTopicPartitions - pausedEventTopicPartitions)
        eventConsumerOrder.verify(eventConsumer, times(1)).poll(any())
        eventConsumerOrder.verify(eventConsumer, times(1)).assignment()
        eventConsumerOrder.verify(eventConsumer, times(1)).resume(listOf(CordaTopicPartition(TOPIC, 3)))

        val stateConsumerOrder = inOrder(stateConsumer)
        stateConsumerOrder.verify(stateConsumer, times(1)).assignment()
        stateConsumerOrder.verify(stateConsumer, times(1)).paused()
        stateConsumerOrder.verify(stateConsumer, never()).poll(any())
        stateConsumerOrder.verify(stateConsumer, never()).resume(any())
    }

    @Test
    fun `uses correct partition assignment when pausing and resuming event consume while waiting for future to complete`() {
        val (stateAndEventListener, eventConsumer, stateConsumer, _) = setupMocks()
        val pausedTopicPartitions = setOf(CordaTopicPartition(TOPIC, 0))
        val assignedTopicPartitions =
            setOf(CordaTopicPartition(TOPIC, 0), CordaTopicPartition(TOPIC, 1), CordaTopicPartition(TOPIC, 2))
        val assignedTopicPartitionsAfterRebalance = setOf(CordaTopicPartition(TOPIC, 0), CordaTopicPartition(TOPIC, 1))

        whenever(eventConsumer.paused())
            .thenReturn(pausedTopicPartitions)

        whenever(eventConsumer.assignment())
            .thenReturn(assignedTopicPartitions)
            .thenReturn(assignedTopicPartitionsAfterRebalance)

        val consumer = StateAndEventConsumerImpl(
            config,
            eventConsumer,
            stateConsumer,
            StateAndEventPartitionState
            (mutableMapOf()),
            stateAndEventListener
        )

        val latch = CountDownLatch(1)
        consumer.waitForFunctionToFinish({
            while (latch.count > 0) {
                Thread.sleep(10)
            }
        }, 200L, "test ")
        latch.countDown()

        verify(eventConsumer, times(1)).paused()
        verify(eventConsumer, times(2)).assignment()
        verify(eventConsumer, times(1)).pause(assignedTopicPartitions - pausedTopicPartitions)
        verify(eventConsumer, times(1)).resume(assignedTopicPartitionsAfterRebalance - pausedTopicPartitions)
        verify(stateConsumer, atLeast(1)).poll(any())
        verify(stateAndEventListener, times(0)).onPartitionSynced(any())
    }

    @Test
    fun `repartition during wait for future to finish throws exception and resumes correct partitions`() {
        val (stateAndEventListener, eventConsumer, stateConsumer, _) = setupMocks()
        val pausedEventTopicPartitions = setOf(CordaTopicPartition(TOPIC, 0), CordaTopicPartition(TOPIC, 1))
        val assignedEventTopicPartitions = setOf(
            CordaTopicPartition(TOPIC, 0),
            CordaTopicPartition(TOPIC, 1),
            CordaTopicPartition(TOPIC, 2),
            CordaTopicPartition(TOPIC, 3)
        )
        val assignedEventTopicPartitionsAfterRebalance = setOf(
            CordaTopicPartition(TOPIC, 0),
            CordaTopicPartition(TOPIC, 1),
            CordaTopicPartition(TOPIC, 3),
            CordaTopicPartition(TOPIC, 4)
        )

        whenever(eventConsumer.paused())
            .thenReturn(pausedEventTopicPartitions)
        whenever(eventConsumer.assignment())
            .thenReturn(assignedEventTopicPartitions)
            .thenReturn(assignedEventTopicPartitionsAfterRebalance)

        val stateAndEventPartitionState =
            StateAndEventPartitionState<String, String>(mutableMapOf(), false)
        val consumer = StateAndEventConsumerImpl(
            config,
            eventConsumer,
            stateConsumer,
            stateAndEventPartitionState,
            stateAndEventListener
        )

        doAnswer {
            stateAndEventPartitionState.dirty = true
            emptyList<String>()
        }.whenever(eventConsumer).poll(any())

        val latch = CountDownLatch(1)
        assertThatThrownBy {
            val future = consumer.waitForFunctionToFinish({
                while (latch.count > 0) {
                    Thread.sleep(10)
                }
            }, Duration.ofDays(1).toMillis(), "test ")

            assertThat(future.isCancelled).isTrue()
        }.isInstanceOf(StateAndEventConsumer.RebalanceInProgressException::class.java)

        val eventConsumerOrder = inOrder(eventConsumer)
        eventConsumerOrder.verify(eventConsumer, times(1)).assignment()
        eventConsumerOrder.verify(eventConsumer, times(1)).paused()
        eventConsumerOrder.verify(eventConsumer, times(1))
            .pause(assignedEventTopicPartitions - pausedEventTopicPartitions)
        eventConsumerOrder.verify(eventConsumer, times(1)).poll(any())
        eventConsumerOrder.verify(eventConsumer, times(1)).assignment()
        eventConsumerOrder.verify(eventConsumer, times(1)).resume(listOf(CordaTopicPartition(TOPIC, 3)))

        verify(stateConsumer, never()).poll(any())
        verify(stateAndEventListener, never()).onPartitionSynced(any())
    }

    @Test
    fun `assigning partitions updates state and event consumers correctly`() {
        val (listener, eventConsumer, stateConsumer, _) = setupMocks()
        val partitions = (0..3).map { CordaTopicPartition(TOPIC, it) }
        val statePartitions = partitions.map { CordaTopicPartition(getStateAndEventStateTopic(it.topic), it.partition) }
        whenever(stateConsumer.beginningOffsets(statePartitions)).thenReturn(
            statePartitions.associateWith { 0L }
        )
        whenever(stateConsumer.endOffsets(statePartitions)).thenReturn(
            // By using the partition number as an offset, one partition should be treated as in sync (0) and the others
            // out of sync.
            statePartitions.associateWith { it.partition.toLong() }
        )
        whenever(stateConsumer.assignment())
            .thenReturn(setOf())
            .thenReturn(statePartitions.toSet())
        val needsSync = partitions.filter {
            it.partition != 0
        }
        val partitionState = StateAndEventPartitionState<String, String>(mutableMapOf(), false)
        val stateAndEventConsumer = StateAndEventConsumerImpl(
            config,
            eventConsumer,
            stateConsumer,
            partitionState,
            listener
        )

        stateAndEventConsumer.onPartitionsAssigned(partitions.toSet())

        val eventConsumerOrder = inOrder(eventConsumer)
        eventConsumerOrder.verify(eventConsumer, times(1)).pause(needsSync)

        val stateConsumerOrder = inOrder(stateConsumer)
        // Check all partitions assigned
        stateConsumerOrder.verify(stateConsumer, times(1)).assignment()
        stateConsumerOrder.verify(stateConsumer, times(1)).assign(statePartitions.toSet())
        // Calculate in sync vs needs syncing
        stateConsumerOrder.verify(stateConsumer, times(1)).beginningOffsets(statePartitions)
        stateConsumerOrder.verify(stateConsumer, times(1)).endOffsets(statePartitions)
        // Set up needs sync partitions and remove those already in sync
        stateConsumerOrder.verify(stateConsumer, times(1)).seekToBeginning(
            needsSync.map { CordaTopicPartition(getStateAndEventStateTopic(it.topic), it.partition) }
        )
        stateConsumerOrder.verify(stateConsumer, times(1)).assignment()
        stateConsumerOrder.verify(stateConsumer, times(1)).assign(
            needsSync.map { CordaTopicPartition(getStateAndEventStateTopic(it.topic), it.partition) }.toSet()
        )

        // Should notify that a partition was synced with no states
        verify(listener).onPartitionSynced(mapOf())
    }

    @Test
    fun `revoking partitions updates the state consumer correctly`() {
        val (listener, eventConsumer, stateConsumer, _) = setupMocks()
        val partitionState = StateAndEventPartitionState<String, String>(mutableMapOf(), false)
        val stateAndEventConsumer = StateAndEventConsumerImpl(
            config,
            eventConsumer,
            stateConsumer,
            partitionState,
            listener
        )
        val partitions = (0..3).map { CordaTopicPartition(TOPIC, it) }.toSet()
        val revokedPartitions = (0..1).map { CordaTopicPartition(TOPIC, it) }.toSet()
        whenever(stateConsumer.assignment()).thenReturn(
            partitions.map { CordaTopicPartition(getStateAndEventStateTopic(it.topic), it.partition) }.toSet()
        )

        stateAndEventConsumer.onPartitionsRevoked(revokedPartitions)

        val stateConsumerOrder = inOrder(stateConsumer)
        stateConsumerOrder.verify(stateConsumer, times(1)).assignment()
        stateConsumerOrder.verify(stateConsumer, times(1)).assign(
            (partitions - revokedPartitions).map { CordaTopicPartition(getStateAndEventStateTopic(it.topic), it.partition) }.toSet()
        )
    }

    @Test
    fun `poll events returns events if some partitions are synced`() {
        val (listener, eventConsumer, stateConsumer, _) = setupMocks()
        val partitionState = StateAndEventPartitionState<String, String>(mutableMapOf(), false)
        val stateAndEventConsumer = StateAndEventConsumerImpl(
            config,
            eventConsumer,
            stateConsumer,
            partitionState,
            listener
        )
        val consumerRecords = listOf(CordaConsumerRecord(TOPIC, 0, 0, "key", "value", 0))
        whenever(eventConsumer.poll(any())).thenReturn(
            consumerRecords
        )
        // Get one partition in sync and three not in sync
        val partitions = (0..3).map { CordaTopicPartition(TOPIC, it) }.toSet()
        val statePartitions = partitions.map { CordaTopicPartition(getStateAndEventStateTopic(it.topic), it.partition) }
        whenever(stateConsumer.beginningOffsets(statePartitions)).thenReturn(
            statePartitions.associateWith { 0L }
        )
        whenever(stateConsumer.endOffsets(statePartitions)).thenReturn(
            // By using the partition number as an offset, one partition should be treated as in sync (0) and the others
            // out of sync.
            statePartitions.associateWith { it.partition.toLong() }
        )
        whenever(stateConsumer.assignment())
            .thenReturn(setOf())
            .thenReturn(statePartitions.toSet())

        stateAndEventConsumer.onPartitionsAssigned(partitions)

        val events = stateAndEventConsumer.pollEvents()
        assertThat(events).isEqualTo(consumerRecords)
        verify(eventConsumer, times(1)).poll(any())

        val events2 = stateAndEventConsumer.pollEvents()
        assertThat(events2).isEqualTo(consumerRecords)
        verify(eventConsumer, times(2)).poll(any())
    }

    @Test
    fun `poll events polls the event consumer if no partitions are assigned`() {
        val (listener, eventConsumer, stateConsumer, _) = setupMocks()
        val partitionState = StateAndEventPartitionState<String, String>(mutableMapOf(), false)
        val stateAndEventConsumer = StateAndEventConsumerImpl(
            config,
            eventConsumer,
            stateConsumer,
            partitionState,
            listener
        )
        whenever(eventConsumer.poll(any())).thenReturn(
            emptyList()
        )
        val events = stateAndEventConsumer.pollEvents()
        assertThat(events).isEmpty()
        verify(eventConsumer, times(1)).poll(any())
        val events2 = stateAndEventConsumer.pollEvents()
        assertThat(events2).isEmpty()
        verify(eventConsumer, times(2)).poll(any())
    }

    @Test
    fun `poll events does not poll the event consumer if it has been polled in the last poll interval and no partitions are in sync`() {
        val (listener, eventConsumer, stateConsumer, _) = setupMocks()
        val partitionState = StateAndEventPartitionState<String, String>(mutableMapOf(), false)
        val stateAndEventConsumer = StateAndEventConsumerImpl(
            config,
            eventConsumer,
            stateConsumer,
            partitionState,
            listener
        )
        whenever(eventConsumer.poll(any())).thenReturn(
            emptyList()
        )
        val partitions = (0..3).map { CordaTopicPartition(TOPIC, it) }.toSet()
        val statePartitions = partitions.map { CordaTopicPartition(getStateAndEventStateTopic(it.topic), it.partition) }
        whenever(stateConsumer.beginningOffsets(statePartitions)).thenReturn(
            statePartitions.associateWith { 0L }
        )
        whenever(stateConsumer.endOffsets(statePartitions)).thenReturn(
            // No partitions in sync but some partitions assigned.
            statePartitions.associateWith { it.partition.toLong() + 1 }
        )
        whenever(stateConsumer.assignment())
            .thenReturn(setOf())
            .thenReturn(statePartitions.toSet())

        stateAndEventConsumer.onPartitionsAssigned(partitions)

        // First poll will update the last polled timestamp, second shouldn't trigger an event consumer poll.
        val events = stateAndEventConsumer.pollEvents()
        assertThat(events).isEmpty()
        verify(eventConsumer, times(1)).poll(any())
        val events2 = stateAndEventConsumer.pollEvents()
        assertThat(events2).isEmpty()
        verify(eventConsumer, times(1)).poll(any())
    }

    @Test
    fun `reset event consumer poll position correctly resets the poll offset`() {
        val (listener, eventConsumer, stateConsumer, _) = setupMocks()
        val partitionState = StateAndEventPartitionState<String, String>(mutableMapOf(), false)
        val stateAndEventConsumer = StateAndEventConsumerImpl(
            config,
            eventConsumer,
            stateConsumer,
            partitionState,
            listener
        )
        stateAndEventConsumer.resetEventOffsetPosition()
        verify(eventConsumer, times(1)).resetToLastCommittedPositions(CordaOffsetResetStrategy.EARLIEST)
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
