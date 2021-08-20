package net.corda.messaging.emulation.topic.model

import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.emulation.properties.SubscriptionConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
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
    private val readLock = mock< ReentrantReadWriteLock.ReadLock>()
    private val sleeper = mock<Condition>()
    private val writeLock = mock< ReentrantReadWriteLock.WriteLock> {
        on { newCondition() } doReturn sleeper
    }
    private val lock = mock<ReentrantReadWriteLock> {
        on { writeLock() } doReturn writeLock
        on { readLock() } doReturn readLock
    }
    private val listener = mock<PartitionAssignmentListener>()

    private val group = ConsumerGroup(
        "topic",
        partitions,
        subscriptionConfig,
        lock,
    )

    @Test
    fun `createConsumption first consumer will add all partitions to the consumer`() {
        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }

        group.createConsumption(consumer)

        assertThat(group.getPartitions(consumer).map { it.first }).containsAll(partitions)
    }

    @Test
    fun `createConsumption second consumer will split all partitions`() {
        val consumerOne = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }
        val consumerTwo = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }

        group.createConsumption(consumerOne)
        group.createConsumption(consumerTwo)

        val firstPartitions =
            group.getPartitions(consumerOne).map { it.first }
        val secondPartitions =
            group.getPartitions(consumerTwo).map { it.first }
        assertThat(firstPartitions + secondPartitions).containsAll(partitions)
    }

    @Test
    fun `createConsumption second consumer will not duplicate subscription`() {
        val consumerOne = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }
        val consumerTwo = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }

        group.createConsumption(consumerOne)
        group.createConsumption(consumerTwo)

        val firstPartitions =
            group.getPartitions(consumerOne).map { it.first }
        val secondPartitions =
            group.getPartitions(consumerTwo).map { it.first }
        val common = firstPartitions.filter {
            secondPartitions.contains(it)
        }

        assertThat(common).isEmpty()
    }

    @Test
    fun `createConsumption second consumer will not leave any consumer without partitions`() {
        val consumerOne = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }
        val consumerTwo = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }

        group.createConsumption(consumerOne)
        group.createConsumption(consumerTwo)

        val firstPartitions =
            group.getPartitions(consumerOne).map { it.first }
        val secondPartitions =
            group.getPartitions(consumerTwo).map { it.first }

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
            override val offsetStrategy = OffsetStrategy.EARLIEST
            override val partitionAssignmentListener = listener

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
        val consumerDefinitions = (1..20).map {
            mock<Consumer> {
                on { offsetStrategy } doReturn OffsetStrategy.LATEST
            }
        }.onEach {
            group.createConsumption(it)
        }

        val partitions = consumerDefinitions.map {
            group.getPartitions(it)
        }

        assertThat(partitions).anySatisfy {
            assertThat(it).isEmpty()
        }
    }

    @Test
    fun `createConsumption will send assign notification`() {
        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionAssignmentListener } doReturn listener
        }

        group.createConsumption(consumer)

        verify(listener).onPartitionsAssigned(partitions.map { "topic" to it.partitionId })
    }

    @Test
    fun `second subscribe will send unassigned notification`() {
        val consumerOne = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionAssignmentListener } doReturn listener
        }
        val consumerTwo = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }

        group.createConsumption(consumerOne)
        group.createConsumption(consumerTwo)

        val secondPartition = group.getPartitions(consumerTwo)
            .map {
                it.first.partitionId
            }
        verify(listener).onPartitionsUnassigned(secondPartition.map { "topic" to it })
    }

    @Test
    fun `createConsumption will return a loop`() {
        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }

        val consumption = group.createConsumption(consumer)

        assertThat(consumption).isInstanceOf(ConsumptionThread::class.java)
    }

    @Test
    fun `second createConsumption will throw an exception`() {
        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }
        group.createConsumption(consumer)

        assertThrows<ConsumerGroup.DuplicateConsumerException> {
            group.createConsumption(consumer)
        }
    }

    @Test
    fun `waitForData will wait for a signal`() {
        group.waitForData()

        verify(sleeper).await(any(), any())
    }

    @Test
    fun `wakeUp will signal`() {
        group.wakeUp()

        verify(sleeper).signalAll()
    }

    @Test
    fun `getPartitions return the correct default latest partitions`() {
        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }

        group.createConsumption(consumer)

        val offsets =
            group.getPartitions(consumer).associate { it.first to it.second }

        assertThat(offsets).containsEntry(partitions[1], partitions[1].latestOffset())
    }

    @Test
    fun `getPartitions return the correct default earliest partitions`() {
        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
        }

        group.createConsumption(consumer)

        val offsets =
            group.getPartitions(consumer).associate { it.first to it.second }

        assertThat(offsets).containsEntry(partitions[1], 0L)
    }

    @Test
    fun `getPartitions return empty list for unknown partition`() {
        val consumer = mock<Consumer>()

        val offsets =
            group.getPartitions(consumer)

        assertThat(offsets).isEmpty()
    }

    @Test
    fun `getPartitions return the latest committed offset`() {
        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
        }

        group.createConsumption(consumer)
        group.commitRecord(
            partitions[2],
            listOf(
                RecordMetadata(offset = 100, partition = 4, record = Record("topic", 1, 2)),
                RecordMetadata(offset = 110, partition = 4, record = Record("topic", 1, 3)),
                RecordMetadata(offset = 130, partition = 4, record = Record("topic", 1, 4)),
                RecordMetadata(offset = 80, partition = 4, record = Record("topic", 1, 5)),
            )
        )

        val offsets =
            group.getPartitions(consumer).associate { it.first to it.second }

        assertThat(offsets).containsEntry(partitions[2], 131L)
    }

    @Test
    fun `commitRecord overwrite the last commit`() {
        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
        }

        group.createConsumption(consumer)
        group.commitRecord(
            partitions[2],
            listOf(
                RecordMetadata(offset = 80, partition = 4, record = Record("topic", 1, 5)),
            )
        )
        group.commitRecord(
            partitions[2],
            listOf(
                RecordMetadata(offset = 90, partition = 4, record = Record("topic", 1, 5)),
            )
        )

        val offsets =
            group.getPartitions(consumer).associate { it.first to it.second }

        assertThat(offsets).containsEntry(partitions[2], 91L)
    }

    @Test
    fun `unsubscribe will send notification`() {

        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
            on { partitionAssignmentListener } doReturn listener
        }

        group.createConsumption(consumer)
        group.stopConsuming(consumer)

        verify(listener).onPartitionsUnassigned(partitions.map { it.partitionId }.map { "topic" to it })
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
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
        }

        group.createConsumption(consumer)
        group.stopConsuming(consumer)

        verify(sleeper, times(2)).signalAll()
    }

    @Test
    fun `unsubscribe will repartition if not the last`() {
        val consumerOne = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
        }
        val consumerTwo = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
        }

        group.createConsumption(consumerOne)
        group.createConsumption(consumerTwo)
        group.stopConsuming(consumerOne)

        assertThat(group.getPartitions(consumerTwo)).hasSize(partitions.size)
    }

    @Test
    fun `isSubscribed will return false after unsubscribe`() {
        val consumerOne = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
        }
        val consumerTwo = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
        }

        group.createConsumption(consumerOne)
        group.createConsumption(consumerTwo)
        group.stopConsuming(consumerOne)

        assertThat(group.isConsuming(consumerOne)).isFalse
    }

    @Test
    fun `isSubscribed will return true if still subscribe`() {
        val consumerOne = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
        }
        val consumerTwo = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
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
            "topic",
            partitions,
            SubscriptionConfiguration(0, Duration.ofMillis(12)),
            lock,
        )

        assertThat(group.pollSizePerPartition).isEqualTo(1)
    }

    private fun createPartition(id: Int): Partition {
        return mock {
            on { partitionId } doReturn id
            on { latestOffset() } doReturn (id * 10).toLong()
        }
    }
}
