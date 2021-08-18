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
    private val loop = mock<Runnable>()
    private val listener = mock<PartitionAssignmentListener>()

    private val group = ConsumerGroup(
        "topic",
        partitions,
        subscriptionConfig,
        lock,
    ) { _, _ -> loop }

    @Test
    fun `subscribe first consumer will add all partitions to the consumer`() {
        val consumerDefinitions = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }

        group.consume(consumerDefinitions)

        assertThat(group.getPartitions(consumerDefinitions).map { it.first }).containsAll(partitions)
    }

    @Test
    fun `subscribe second consumer will split all partitions`() {
        val consumerDefinitionsOne = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }
        val consumerDefinitionsTwo = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }

        group.consume(consumerDefinitionsOne)
        group.consume(consumerDefinitionsTwo)

        val firstPartitions =
            group.getPartitions(consumerDefinitionsOne).map { it.first }
        val secondPartitions =
            group.getPartitions(consumerDefinitionsTwo).map { it.first }
        assertThat(firstPartitions + secondPartitions).containsAll(partitions)
    }

    @Test
    fun `subscribe second consumer will not duplicate subscription`() {
        val consumerDefinitionsOne = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }
        val consumerDefinitionsTwo = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }

        group.consume(consumerDefinitionsOne)
        group.consume(consumerDefinitionsTwo)

        val firstPartitions =
            group.getPartitions(consumerDefinitionsOne).map { it.first }
        val secondPartitions =
            group.getPartitions(consumerDefinitionsTwo).map { it.first }
        val common = firstPartitions.filter {
            secondPartitions.contains(it)
        }

        assertThat(common).isEmpty()
    }

    @Test
    fun `subscribe second consumer will not leave any consumer without partitions`() {
        val consumerDefinitionsOne = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }
        val consumerDefinitionsTwo = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }

        group.consume(consumerDefinitionsOne)
        group.consume(consumerDefinitionsTwo)

        val firstPartitions =
            group.getPartitions(consumerDefinitionsOne).map { it.first }
        val secondPartitions =
            group.getPartitions(consumerDefinitionsTwo).map { it.first }

        assertSoftly {
            it.assertThat(firstPartitions).isNotEmpty
            it.assertThat(secondPartitions).isNotEmpty
        }
    }

    @Test
    fun `subscribe second consumer will with all in one will give all the partition to one consumer`() {
        val consumerDefinitionsOne = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.allInFirst
        }
        val consumerDefinitionsTwo = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.allInFirst
        }

        group.consume(consumerDefinitionsOne)
        group.consume(consumerDefinitionsTwo)

        val firstPartitions =
            group.getPartitions(consumerDefinitionsOne).map { it.first }
        val secondPartitions =
            group.getPartitions(consumerDefinitionsTwo).map { it.first }
        val allPartitions = setOf(firstPartitions.size, secondPartitions.size)
        assertThat(allPartitions).contains(0, 5)
    }

    @Test
    fun `subscribe will remove old subscriptions`() {
        class Definitions(private val index: Int) : ConsumerDefinitions {
            override val groupName = "group"
            override val topicName = "topic"
            override val offsetStrategy = OffsetStrategy.EARLIEST
            override val partitionStrategy = PartitionStrategy.allInFirst
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
                group.consume(it)
            }

        verify(listener, atLeast(1)).onPartitionsUnassigned(any())
    }

    @Test
    fun `subscribe more consumer than partitions will not assign any partition to the last consumer`() {
        val consumerDefinitions = (1..20).map {
            mock<ConsumerDefinitions> {
                on { offsetStrategy } doReturn OffsetStrategy.LATEST
                on { partitionStrategy } doReturn PartitionStrategy.modulo
            }
        }.onEach {
            group.consume(it)
        }

        val partitions = consumerDefinitions.map {
            group.getPartitions(it)
        }

        assertThat(partitions).anySatisfy {
            assertThat(it).isEmpty()
        }
    }

    @Test
    fun `subscribe will send assign notification`() {
        val consumerDefinitions = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionAssignmentListener } doReturn listener
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }

        group.consume(consumerDefinitions)

        verify(listener).onPartitionsAssigned(partitions.map { "topic" to it.partitionId })
    }

    @Test
    fun `second subscribe will send unassigned notification`() {
        val consumerDefinitionsOne = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionAssignmentListener } doReturn listener
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }
        val consumerDefinitionsTwo = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }

        group.consume(consumerDefinitionsOne)
        group.consume(consumerDefinitionsTwo)

        val secondPartition = group.getPartitions(consumerDefinitionsTwo)
            .map {
                it.first.partitionId
            }
        verify(listener).onPartitionsUnassigned(secondPartition.map { "topic" to it })
    }

    @Test
    fun `subscribe will start the loop`() {
        val consumerDefinitions = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }

        group.consume(consumerDefinitions)

        verify(loop).run()
    }

    @Test
    fun `second subscribe will throw an exception`() {
        val consumerDefinitions = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }
        group.consume(consumerDefinitions)

        assertThrows<ConsumerGroup.DuplicateConsumerException> {
            group.consume(consumerDefinitions)
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
        val consumerDefinitions = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }

        group.consume(consumerDefinitions)

        val offsets =
            group.getPartitions(consumerDefinitions).associate { it.first to it.second }

        assertThat(offsets).containsEntry(partitions[1], partitions[1].latestOffset())
    }

    @Test
    fun `getPartitions return the correct default earliest partitions`() {
        val consumerDefinitions = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }

        group.consume(consumerDefinitions)

        val offsets =
            group.getPartitions(consumerDefinitions).associate { it.first to it.second }

        assertThat(offsets).containsEntry(partitions[1], 0L)
    }

    @Test
    fun `getPartitions return empty list for unknown partition`() {
        val consumerDefinitions = mock<ConsumerDefinitions>()

        val offsets =
            group.getPartitions(consumerDefinitions)

        assertThat(offsets).isEmpty()
    }

    @Test
    fun `getPartitions return the latest committed offset`() {
        val consumerDefinitions = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }

        group.consume(consumerDefinitions)
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
            group.getPartitions(consumerDefinitions).associate { it.first to it.second }

        assertThat(offsets).containsEntry(partitions[2], 131L)
    }

    @Test
    fun `commitRecord overwrite the last commit`() {
        val consumerDefinitions = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }

        group.consume(consumerDefinitions)
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
            group.getPartitions(consumerDefinitions).associate { it.first to it.second }

        assertThat(offsets).containsEntry(partitions[2], 91L)
    }

    @Test
    fun `unsubscribe will send notification`() {

        val consumerDefinitions = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
            on { partitionAssignmentListener } doReturn listener
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }

        group.consume(consumerDefinitions)
        group.stopConsuming(consumerDefinitions)

        verify(listener).onPartitionsUnassigned(partitions.map { it.partitionId }.map { "topic" to it })
    }

    @Test
    fun `unsubscribe will not send notification if consumer was not subscribe`() {
        val consumerDefinitions = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
            on { partitionAssignmentListener } doReturn listener
        }

        group.stopConsuming(consumerDefinitions)

        verify(listener, never()).onPartitionsUnassigned(partitions.map { it.partitionId }.map { "topic" to it })
    }

    @Test
    fun `unsubscribe will wake up in the last consumer`() {
        val consumerDefinitions = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }

        group.consume(consumerDefinitions)
        group.stopConsuming(consumerDefinitions)

        verify(sleeper, times(2)).signalAll()
    }

    @Test
    fun `unsubscribe will repartition if not the last`() {
        val consumerDefinitionsOne = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }
        val consumerDefinitionsTwo = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }

        group.consume(consumerDefinitionsOne)
        group.consume(consumerDefinitionsTwo)
        group.stopConsuming(consumerDefinitionsOne)

        assertThat(group.getPartitions(consumerDefinitionsTwo)).hasSize(partitions.size)
    }

    @Test
    fun `isSubscribed will return false after unsubscribe`() {
        val consumerDefinitionsOne = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }
        val consumerDefinitionsTwo = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }

        group.consume(consumerDefinitionsOne)
        group.consume(consumerDefinitionsTwo)
        group.stopConsuming(consumerDefinitionsOne)

        assertThat(group.isConsuming(consumerDefinitionsOne)).isFalse
    }

    @Test
    fun `isSubscribed will return true if still subscribe`() {
        val consumerDefinitionsOne = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }
        val consumerDefinitionsTwo = mock<ConsumerDefinitions> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
            on { partitionStrategy } doReturn PartitionStrategy.modulo
        }

        group.consume(consumerDefinitionsOne)
        group.consume(consumerDefinitionsTwo)
        group.stopConsuming(consumerDefinitionsOne)

        assertThat(group.isConsuming(consumerDefinitionsTwo)).isTrue
    }

    private fun createPartition(id: Int): Partition {
        return mock {
            on { partitionId } doReturn id
            on { latestOffset() } doReturn (id * 10).toLong()
        }
    }
}
