package net.corda.messaging.emulation.topic.model

import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import net.corda.messaging.emulation.properties.SubscriptionConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.time.Duration
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantReadWriteLock

class ConsumerGroupTest {
    private val partitions = (1..5).map { createPartition(it + 4) }
    private val subscriptionConfig = SubscriptionConfiguration(10, Duration.ofMillis(100))
    private val readLock = mock<ReentrantReadWriteLock.ReadLock>()
    private val sleeper = mock<Condition>()
    private val writeLock = mock<ReentrantReadWriteLock.WriteLock> {
        on { newCondition() } doReturn sleeper
    }
    private val lock = mock<ReentrantReadWriteLock> {
        on { writeLock() } doReturn writeLock
        on { readLock() } doReturn readLock
    }
    val capturePartitions = argumentCaptor<List<Pair<String, Int>>>()
    val captureUnpartitions = argumentCaptor<List<Pair<String, Int>>>()
    private val listener = mock<PartitionAssignmentListener> {
        on { onPartitionsAssigned(capturePartitions.capture()) } doAnswer {}
        on { onPartitionsUnassigned(captureUnpartitions.capture()) } doAnswer {}
    }
    private val firstConsumer = mock<Consumer> {
        on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
        on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
        on { offsetStrategy } doReturn OffsetStrategy.LATEST
    }

    private val group = ConsumerGroup(
        partitions,
        subscriptionConfig,
        firstConsumer,
        lock,
    )

    @Test
    fun `createConsumption first consumer will add all partitions to the consumer`() {
        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
            on { partitionAssignmentListener } doReturn listener
        }

        group.createConsumption(consumer)

        assertThat(capturePartitions.firstValue.map { it.second }).containsAll(partitions.map { it.partitionId })
    }

    @Test
    fun `createConsumption second consumer will split all partitions`() {
        val consumerOne = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
            on { partitionAssignmentListener } doReturn listener
        }
        val consumerTwo = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
            on { partitionAssignmentListener } doReturn listener
        }

        group.createConsumption(consumerOne)
        group.createConsumption(consumerTwo)

        val firstPartitions =
            capturePartitions.firstValue.map { it.second }
        val secondPartitions =
            capturePartitions.secondValue.map { it.second }
        assertThat(firstPartitions + secondPartitions).containsAll(partitions.map { it.partitionId })
    }

    @Test
    fun `createConsumption second consumer will not duplicate subscription`() {
        val consumerOne = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
            on { partitionAssignmentListener } doReturn listener
        }
        val consumerTwo = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
            on { partitionAssignmentListener } doReturn listener
        }

        group.createConsumption(consumerOne)
        group.createConsumption(consumerTwo)

        val firstPartitions =
            capturePartitions.firstValue.map { it.second } -
                captureUnpartitions.firstValue.map { it.second }
        val secondPartitions =
            capturePartitions.secondValue.map { it.second }
        val common = firstPartitions.filter {
            secondPartitions.contains(it)
        }

        assertThat(common).isEmpty()
    }

    @Test
    fun `createConsumption second consumer will not leave any consumer without partitions`() {
        val consumerOne = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
            on { partitionAssignmentListener } doReturn listener
        }
        val consumerTwo = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
            on { partitionAssignmentListener } doReturn listener
        }

        group.createConsumption(consumerOne)
        group.createConsumption(consumerTwo)

        val firstPartitions =
            capturePartitions.firstValue.map { it.second }
        val secondPartitions =
            capturePartitions.secondValue.map { it.second }

        assertSoftly {
            it.assertThat(firstPartitions).isNotEmpty
            it.assertThat(secondPartitions).isNotEmpty
        }
    }

    @Test
    fun `createConsumption will remove old subscriptions`() {
        class Definitions(private val index: Int) : Consumer {
            override val groupName = "group"
            override val topicName = "topic"
            override val offsetStrategy = OffsetStrategy.LATEST
            override val partitionAssignmentListener = listener
            override val commitStrategy = CommitStrategy.COMMIT_AFTER_PROCESSING
            override val partitionStrategy = PartitionStrategy.DIVIDE_PARTITIONS

            override fun handleRecords(records: Collection<RecordMetadata>) {
            }

            override fun hashCode(): Int {
                // Override hash code to secure order inside the hash map
                return 20 - index
            }
        }

        (1..20).map { index -> Definitions(index) }
            .forEach {
                group.createConsumption(it)
            }

        verify(listener, atLeast(1)).onPartitionsUnassigned(any())
    }

    @Test
    fun `createConsumption more consumer than partitions will not assign any partition to the last consumer`() {
        val partitionsPerConsumer = mutableMapOf<Int, MutableSet<Int>>()
        (1..20).map { consumerId ->
            val myPartitions = mutableSetOf<Int>()
            partitionsPerConsumer[consumerId] = myPartitions
            mock<PartitionAssignmentListener> {
                on { onPartitionsUnassigned(any()) } doAnswer {
                    val partitions = it.getArgument<List<Pair<String, Int>>>(0).map { it.second }
                    myPartitions.removeAll(partitions)
                    Unit
                }
                on { onPartitionsAssigned(any()) } doAnswer {
                    val partitions = it.getArgument<List<Pair<String, Int>>>(0).map { it.second }
                    myPartitions.addAll(partitions)
                    Unit
                }
            }
        }
            .map { listener ->
                mock<Consumer> {
                    on { offsetStrategy } doReturn OffsetStrategy.LATEST
                    on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
                    on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
                    on { partitionAssignmentListener } doReturn listener
                }
            }.forEach {
                group.createConsumption(it)
            }

        assertThat(partitionsPerConsumer.values).anySatisfy {
            assertThat(it).isEmpty()
        }
    }

    @Test
    fun `createConsumption will return a loop`() {
        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
        }

        val consumption = group.createConsumption(consumer)

        assertThat(consumption).isInstanceOf(ConsumptionThread::class.java)
    }

    @Test
    fun `second createConsumption will throw an exception`() {
        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
        }
        group.createConsumption(consumer)

        assertThrows<ConsumerGroup.DuplicateConsumerException> {
            group.createConsumption(consumer)
        }
    }

    @Test
    fun `waitForPhaseChange will wait for phase if not the same`() {
        val phase = group.currentPhase()
        group.waitForPhaseChange(phase)

        verify(sleeper).await(any(), any())
    }

    @Test
    fun `waitForPhaseChange will not wait if phase changed`() {
        val phase = group.currentPhase()
        group.waitForPhaseChange(phase - 1)

        verify(sleeper, never()).await(any(), any())
    }

    @Test
    fun `wakeUp will signal`() {
        group.wakeUp()

        verify(sleeper).signalAll()
    }

    @Test
    fun `wakeUp will change the phase`() {
        val phase = group.currentPhase()

        group.wakeUp()

        assertThat(group.currentPhase()).isNotEqualTo(phase)
    }

    @Test
    fun `addPartitionsToLoop return the correct default latest partitions`() {
        val loop = mock<ConsumptionLoop>()

        group.addPartitionsToLoop(loop, listOf(partitions[1], partitions[2]))

        verify(loop).addPartitions(
            mapOf(
                partitions[1] to partitions[1].latestOffset(),
                partitions[2] to partitions[2].latestOffset()
            )
        )
    }

    @Test
    fun `addPartitionsToLoop return the correct default earrliest partitions`() {
        val firstConsumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
        }

        val group = ConsumerGroup(
            partitions,
            subscriptionConfig,
            firstConsumer,
            lock,
        )
        val loop = mock<ConsumptionLoop>()

        group.addPartitionsToLoop(loop, listOf(partitions[1], partitions[2]))

        verify(loop).addPartitions(
            mapOf(
                partitions[1] to 0,
                partitions[2] to 0
            )
        )
    }

    @Test
    fun `addPartitionsToLoop return latest committed offset`() {
        val loop = mock<ConsumptionLoop>()
        group.commit(
            mapOf(partitions[2] to 131)
        )

        group.addPartitionsToLoop(loop, listOf(partitions[2]))

        verify(loop).addPartitions(
            mapOf(
                partitions[2] to 131
            )
        )
    }

    @Test
    fun `commit overwrite the last commit`() {
        val loop = mock<ConsumptionLoop>()
        group.commit(
            mapOf(partitions[2] to 80)
        )
        group.commit(
            mapOf(partitions[2] to 91)
        )

        group.addPartitionsToLoop(loop, listOf(partitions[2]))

        verify(loop).addPartitions(
            mapOf(
                partitions[2] to 91
            )
        )
    }

    @Test
    fun `unsubscribe will not send notification if consumer was not subscribe`() {
        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
            on { partitionAssignmentListener } doReturn listener
        }

        group.stopConsuming(consumer)

        verify(listener, never()).onPartitionsUnassigned(partitions.map { it.partitionId }.map { "topic" to it })
    }

    @Test
    fun `unsubscribe will wake up in the last consumer`() {
        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
        }

        group.createConsumption(consumer)
        group.stopConsuming(consumer)

        verify(sleeper, times(2)).signalAll()
    }

    @Test
    fun `unsubscribe will repartition if not the last`() {
        val consumerOne = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
        }
        val consumerTwo = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
            on { partitionAssignmentListener } doReturn listener
        }

        group.createConsumption(consumerOne)
        group.createConsumption(consumerTwo)
        group.stopConsuming(consumerOne)

        assertThat(capturePartitions.allValues.flatten()).hasSize(partitions.size)
    }

    @Test
    fun `isSubscribed will return false after unsubscribe`() {
        val consumerOne = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
        }
        val consumerTwo = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
        }

        group.createConsumption(consumerOne)
        group.createConsumption(consumerTwo)
        group.stopConsuming(consumerOne)

        assertThat(group.isConsuming(consumerOne)).isFalse
    }

    @Test
    fun `isSubscribed will return true if still subscribe`() {
        val consumerOne = mock<Consumer> {
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }
        val consumerTwo = mock<Consumer> {
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }

        group.createConsumption(consumerOne)
        group.createConsumption(consumerTwo)
        group.stopConsuming(consumerOne)

        assertThat(group.isConsuming(consumerTwo)).isTrue
    }

    @Test
    fun `pollSizePerPartition return the correct value`() {
        assertThat(group.pollSizePerPartition).isEqualTo(2)
    }

    @Test
    fun `pollSizePerPartition return no less than one`() {
        val partitions = (1..30).map { createPartition(it + 4) }
        val group = ConsumerGroup(
            partitions,
            SubscriptionConfiguration(0, Duration.ofMillis(12)),
            firstConsumer,
            lock,
        )

        assertThat(group.pollSizePerPartition).isEqualTo(1)
    }

    @Test
    fun `repartitionShare will share the partition to all the consumers`() {
        val consumers = (1..4).map {
            mock<Consumer> {
                on { commitStrategy } doReturn CommitStrategy.NO_COMMIT
                on { partitionStrategy } doReturn PartitionStrategy.SHARE_PARTITIONS
                on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
                on { partitionAssignmentListener } doReturn listener
                on { topicName } doReturn "topic"
            }
        }
        val group = ConsumerGroup(
            partitions,
            subscriptionConfig,
            consumers[0],
            lock,
        )
        consumers.forEach {
            group.createConsumption(it)
        }

        verify(listener, times(consumers.size)).onPartitionsAssigned(partitions.map { "topic" to it.partitionId })
    }

    @Test
    fun `adding manual subscription will not send any event`() {
        val consumers = (1..4).map {
            mock<Consumer> {
                on { commitStrategy } doReturn CommitStrategy.NO_COMMIT
                on { partitionStrategy } doReturn PartitionStrategy.MANUAL
                on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
                on { partitionAssignmentListener } doReturn listener
                on { topicName } doReturn "topic"
            }
        }
        val group = ConsumerGroup(
            partitions,
            subscriptionConfig,
            consumers[0],
            lock,
        )
        consumers.forEach {
            group.createConsumption(it)
        }

        verify(listener, never()).onPartitionsAssigned(any())
    }

    @Test
    fun `repartitionShare will signal the lock`() {
        val consumers = (1..4).map {
            mock<Consumer> {
                on { commitStrategy } doReturn CommitStrategy.NO_COMMIT
                on { partitionStrategy } doReturn PartitionStrategy.SHARE_PARTITIONS
                on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
            }
        }
        val group = ConsumerGroup(
            partitions,
            subscriptionConfig,
            consumers[0],
            lock,
        )
        consumers.forEach {
            group.createConsumption(it)
        }

        verify(sleeper, times(consumers.size)).signalAll()
    }

    @Test
    fun `createConsumption with the wrong commit strategy will break`() {
        val consumer = mock<Consumer> {
            on { commitStrategy } doReturn CommitStrategy.NO_COMMIT
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
        }
        assertThrows<IllegalStateException> {
            group.createConsumption(consumer)
        }
    }

    @Test
    fun `createConsumption with the wrong partition strategy will break`() {
        val consumer = mock<Consumer> {
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
            on { partitionStrategy } doReturn PartitionStrategy.SHARE_PARTITIONS
        }
        assertThrows<IllegalStateException> {
            group.createConsumption(consumer)
        }
    }

    @Test
    fun `createConsumption with the wrong offset strategy will break`() {
        val consumer = mock<Consumer> {
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
        }
        assertThrows<IllegalStateException> {
            group.createConsumption(consumer)
        }
    }

    @Test
    fun `createConsumption with return the correct killable object`() {
        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
            on { partitionAssignmentListener } doReturn listener
        }
        val consumption = group.createConsumption(consumer)
        consumption.close()

        verify(listener).onPartitionsUnassigned(any())
    }

    @Test
    fun `assignPartition with non manual partition strategy will throw an exception`() {
        val consumer = mock<Consumer> {
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }
        val partitions = (1..30).map { createPartition(it + 4) }
        val group = ConsumerGroup(
            partitions,
            SubscriptionConfiguration(0, Duration.ofMillis(12)),
            consumer,
            lock,
        )
        group.createConsumption(consumer)

        assertThrows<IllegalStateException> {
            group.assignPartition(consumer, partitions.take(4))
        }
    }

    @Test
    fun `assignPartition with un known partition will throw an exception`() {
        val consumer = mock<Consumer> {
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }
        val partitions = (1..30).map { createPartition(it + 4) }
        val group = ConsumerGroup(
            partitions,
            SubscriptionConfiguration(0, Duration.ofMillis(12)),
            firstConsumer,
            lock,
        )
        group.createConsumption(firstConsumer)

        assertThrows<IllegalStateException> {
            group.assignPartition(consumer, partitions.take(4))
        }
    }

    @Test
    fun `assignPartition with valid data will assign the partitions`() {
        val consumer = mock<Consumer> {
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
            on { partitionStrategy } doReturn PartitionStrategy.MANUAL
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionAssignmentListener } doReturn listener
            on { topicName } doReturn "topic"
        }
        val partitions = (1..30).map { createPartition(it + 4) }
        val group = ConsumerGroup(
            partitions,
            SubscriptionConfiguration(0, Duration.ofMillis(12)),
            consumer,
            lock,
        )
        group.createConsumption(consumer)

        group.assignPartition(consumer, partitions.take(3))

        assertThat(capturePartitions.firstValue).containsExactly("topic" to 5, "topic" to 6, "topic" to 7)
    }

    @Test
    fun `assignPartition will wake up the consumers`() {
        val consumer = mock<Consumer> {
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
            on { partitionStrategy } doReturn PartitionStrategy.MANUAL
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionAssignmentListener } doReturn listener
            on { topicName } doReturn "topic"
        }
        val partitions = (1..30).map { createPartition(it + 4) }
        val group = ConsumerGroup(
            partitions,
            SubscriptionConfiguration(0, Duration.ofMillis(12)),
            consumer,
            lock,
        )
        group.createConsumption(consumer)

        group.assignPartition(consumer, partitions.take(3))

        verify(sleeper).signalAll()
    }

    @Test
    fun `unAssignPartition with non manual partition strategy will throw an exception`() {
        val consumer = mock<Consumer> {
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }
        val partitions = (1..30).map { createPartition(it + 4) }
        val group = ConsumerGroup(
            partitions,
            SubscriptionConfiguration(0, Duration.ofMillis(12)),
            consumer,
            lock,
        )
        group.createConsumption(consumer)

        assertThrows<IllegalStateException> {
            group.unAssignPartition(consumer, partitions.take(4))
        }
    }

    @Test
    fun `unAssignPartition with un known partition will throw an exception`() {
        val consumer = mock<Consumer> {
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
            on { partitionStrategy } doReturn PartitionStrategy.DIVIDE_PARTITIONS
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }
        val partitions = (1..30).map { createPartition(it + 4) }
        val group = ConsumerGroup(
            partitions,
            SubscriptionConfiguration(0, Duration.ofMillis(12)),
            firstConsumer,
            lock,
        )
        group.createConsumption(firstConsumer)

        assertThrows<IllegalStateException> {
            group.unAssignPartition(consumer, partitions.take(4))
        }
    }

    @Test
    fun `unAssignPartition with valid data will remove the partitions`() {
        val consumer = mock<Consumer> {
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
            on { partitionStrategy } doReturn PartitionStrategy.MANUAL
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionAssignmentListener } doReturn listener
            on { topicName } doReturn "topic"
        }
        val partitions = (1..30).map { createPartition(it + 4) }
        val group = ConsumerGroup(
            partitions,
            SubscriptionConfiguration(0, Duration.ofMillis(12)),
            consumer,
            lock,
        )
        group.createConsumption(consumer)

        group.unAssignPartition(consumer, partitions.take(3))

        assertThat(captureUnpartitions.firstValue).containsExactly("topic" to 5, "topic" to 6, "topic" to 7)
    }

    @Test
    fun `unAssignPartition will wake up the consumers`() {
        val consumer = mock<Consumer> {
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
            on { partitionStrategy } doReturn PartitionStrategy.MANUAL
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionAssignmentListener } doReturn listener
            on { topicName } doReturn "topic"
        }
        val partitions = (1..30).map { createPartition(it + 4) }
        val group = ConsumerGroup(
            partitions,
            SubscriptionConfiguration(0, Duration.ofMillis(12)),
            consumer,
            lock,
        )
        group.createConsumption(consumer)

        group.unAssignPartition(consumer, partitions.take(3))

        verify(sleeper).signalAll()
    }

    private fun createPartition(id: Int): Partition {
        return mock {
            on { partitionId } doReturn id
            on { topicName } doReturn "topic $id"
            on { latestOffset() } doReturn (id * 10).toLong()
        }
    }
}
