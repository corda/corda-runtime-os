package net.corda.messaging.emulation.topic.model

import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.emulation.properties.SubscriptionConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock

class ConsumerGroupTest {
    private val partitions = (1..5).map { createPartition(it + 4) }
    private val subscriptionConfig = SubscriptionConfiguration(10, 100)
    private val readLock = mock<Lock>()
    private val sleeper = mock<Condition>()
    private val writeLock = mock<Lock> {
        on { newCondition() } doReturn sleeper
    }
    private val lock = mock<ReadWriteLock> {
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
        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }

        group.subscribe(consumer)

        assertThat(group.getPartitions(consumer)?.map { it.first }).containsAll(partitions)
    }

    @Test
    fun `subscribe second consumer will split all partitions`() {
        val consumerOne = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }
        val consumerTwo = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }

        group.subscribe(consumerOne)
        group.subscribe(consumerTwo)

        val firstPartitions =
            group.getPartitions(consumerOne)?.map { it.first } ?: emptyList()
        val secondPartitions =
            group.getPartitions(consumerTwo)?.map { it.first } ?: emptyList()
        assertThat(firstPartitions + secondPartitions).containsAll(partitions)
    }

    @Test
    fun `subscribe second consumer will not duplicate subscription`() {
        val consumerOne = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }
        val consumerTwo = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }

        group.subscribe(consumerOne)
        group.subscribe(consumerTwo)

        val firstPartitions =
            group.getPartitions(consumerOne)?.map { it.first } ?: emptyList()
        val secondPartitions =
            group.getPartitions(consumerTwo)?.map { it.first } ?: emptyList()
        val common = firstPartitions.filter {
            secondPartitions.contains(it)
        }

        assertThat(common).isEmpty()
    }

    @Test
    fun `subscribe second consumer will not leave any consumer without partitions`() {
        val consumerOne = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }
        val consumerTwo = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }

        group.subscribe(consumerOne)
        group.subscribe(consumerTwo)

        val firstPartitions =
            group.getPartitions(consumerOne)?.map { it.first } ?: emptyList()
        val secondPartitions =
            group.getPartitions(consumerTwo)?.map { it.first } ?: emptyList()

        assertSoftly {
            it.assertThat(firstPartitions).isNotEmpty
            it.assertThat(secondPartitions).isNotEmpty()
        }
    }

    @Test
    fun `subscribe more consumer than partitions will not assign any partition to the last consumer`() {
        val consumers = (1..20).map {
            mock<Consumer> {
                on { offsetStrategy } doReturn OffsetStrategy.LATEST
            }
        }.onEach {
            group.subscribe(it)
        }

        val partitions = consumers.map {
            group.getPartitions(it)
        }

        assertThat(partitions).anySatisfy {
            assertThat(it).isEmpty()
        }
    }

    @Test
    fun `subscribe will send assign notification`() {
        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
            on { partitionAssignmentListener } doReturn listener
        }

        group.subscribe(consumer)

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

        group.subscribe(consumerOne)
        group.subscribe(consumerTwo)

        val secondPartition = group.getPartitions(consumerTwo)
            ?.map {
                it.first.partitionId
            }
        verify(listener).onPartitionsUnassigned(secondPartition?.map { "topic" to it } ?: emptyList())
    }

    @Test
    fun `subscribe will start the loop`() {
        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.LATEST
        }

        group.subscribe(consumer)

        verify(loop).run()
    }

    @Test
    fun `waitForDate will wait for a signal`() {
        group.waitForDate()

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

        group.subscribe(consumer)

        val offsets =
            group.getPartitions(consumer)?.associate { it.first to it.second } ?: emptyMap()

        assertThat(offsets).containsEntry(partitions[1], partitions[1].latestOffset())
    }

    @Test
    fun `getPartitions return the correct default earliest partitions`() {
        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
        }

        group.subscribe(consumer)

        val offsets =
            group.getPartitions(consumer)?.associate { it.first to it.second } ?: emptyMap()

        assertThat(offsets).containsEntry(partitions[1], 0L)
    }

    @Test
    fun `getPartitions return null for unknown partition`() {
        val consumer = mock<Consumer>()

        val offsets =
            group.getPartitions(consumer)

        assertThat(offsets).isNull()
    }

    @Test
    fun `getPartitions return the latest committed offset`() {
        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
        }

        group.subscribe(consumer)
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
            group.getPartitions(consumer)?.associate { it.first to it.second } ?: emptyMap()

        assertThat(offsets).containsEntry(partitions[2], 130L)
    }

    @Test
    fun `commitRecord overwrite the last commit`() {
        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
        }

        group.subscribe(consumer)
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
            group.getPartitions(consumer)?.associate { it.first to it.second } ?: emptyMap()

        assertThat(offsets).containsEntry(partitions[2], 90L)
    }

    @Test
    fun `unsubscribe will send notification`() {

        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
            on { partitionAssignmentListener } doReturn listener
        }

        group.subscribe(consumer)
        group.unsubscribe(consumer)

        verify(listener).onPartitionsUnassigned(partitions.map { it.partitionId }.map { "topic" to it })
    }

    @Test
    fun `unsubscribe will not send notification if consumer was not subscribe`() {
        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
            on { partitionAssignmentListener } doReturn listener
        }

        group.unsubscribe(consumer)

        verify(listener, never()).onPartitionsUnassigned(partitions.map { it.partitionId }.map { "topic" to it })
    }

    @Test
    fun `unsubscribe will wake up in the last consumer`() {
        val consumer = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
        }

        group.subscribe(consumer)
        group.unsubscribe(consumer)

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

        group.subscribe(consumerOne)
        group.subscribe(consumerTwo)
        group.unsubscribe(consumerOne)

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

        group.subscribe(consumerOne)
        group.subscribe(consumerTwo)
        group.unsubscribe(consumerOne)

        assertThat(group.isSubscribed(consumerOne)).isFalse
    }

    @Test
    fun `isSubscribed will return true if still subscribe`() {
        val consumerOne = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
        }
        val consumerTwo = mock<Consumer> {
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
        }

        group.subscribe(consumerOne)
        group.subscribe(consumerTwo)
        group.unsubscribe(consumerOne)

        assertThat(group.isSubscribed(consumerTwo)).isTrue
    }

    private fun createPartition(id: Int): Partition {
        return mock {
            on { partitionId } doReturn id
            on { latestOffset() } doReturn (id * 10).toLong()
        }
    }
}
