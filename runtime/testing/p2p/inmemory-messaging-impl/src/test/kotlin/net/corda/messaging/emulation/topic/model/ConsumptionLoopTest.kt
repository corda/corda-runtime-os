package net.corda.messaging.emulation.topic.model

import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.properties.SubscriptionConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.util.concurrent.locks.ReentrantReadWriteLock

class ConsumptionLoopTest {
    private val config = SubscriptionConfiguration(10, Duration.ofSeconds(1))
    private val records = mutableListOf<RecordMetadata>()
    private val consumer = mock<Consumer> {
        on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
        on { handleRecords(any()) } doAnswer {
            records.addAll(it.getArgument(0) as Collection<RecordMetadata>)
            Unit
        }
    }
    private val group = mock<ConsumerGroup> {
        on { subscriptionConfig } doReturn config
        on { pollSizePerPartition } doReturn 5
        on { lock } doReturn ReentrantReadWriteLock()
    }

    private val loop = ConsumptionLoop(consumer, group)

    @Test
    fun `run will not ask for records as long as the consumer is subscribe`() {
        whenever(group.isConsuming(consumer))
            .thenReturn(true)
            .thenReturn(true)
            .thenReturn(true)
            .thenReturn(false)
        val partition = mock<Partition>()
        loop.addPartitions(mapOf(partition to 10L))

        loop.run()

        verify(partition, times(3)).getRecordsFrom(any(), any())
    }

    @Test
    fun `readRecords will ignore the consumer if it has no partitions`() {
        whenever(group.isConsuming(consumer))
            .thenReturn(true)
            .thenReturn(false)

        loop.run()

        verify(consumer, never()).handleRecords(any())
    }

    @Test
    fun `readRecords read the correct records`() {
        val partition = mock<Partition> {
            on { getRecordsFrom(any(), any()) } doReturn emptyList()
        }
        whenever(group.isConsuming(consumer))
            .thenReturn(true)
            .thenReturn(false)
        loop.addPartitions(mapOf(partition to 1004L))

        loop.run()

        verify(partition).getRecordsFrom(1004L, 5)
    }

    @Test
    fun `readRecords wil not ignore non empty list`() {
        val recordsToSend = (1..7).map {
            RecordMetadata(it.toLong(), Record("topic", it, it), 1)
        }
        val partition = mock<Partition> {
            on { getRecordsFrom(any(), any()) } doReturn recordsToSend
        }
        whenever(group.isConsuming(consumer))
            .thenReturn(true)
            .thenReturn(false)
        loop.addPartitions(mapOf(partition to 1004L))

        loop.run()

        assertThat(records).containsAll(recordsToSend)
    }

    @Test
    fun `processRecords will commit the records to the correct partition`() {
        val partitionTwoRecords = (10..15).map {
            RecordMetadata(it.toLong(), Record("topic", it, it), 2)
        }
        val partitionOneRecords = (10..15).map {
            RecordMetadata(it.toLong(), Record("topic", it, it), 2)
        }
        val partitionOne = mock<Partition> {
            on { getRecordsFrom(any(), any()) } doReturn partitionOneRecords
        }
        val partitionTwo = mock<Partition> {
            on { getRecordsFrom(any(), any()) } doReturn partitionTwoRecords
        }
        whenever(group.isConsuming(consumer))
            .thenReturn(true)
            .thenReturn(false)
        loop.addPartitions(
            mapOf(
                partitionOne to 1004L,
                partitionTwo to 1001L
            )
        )

        loop.run()

        verify(group).commit(
            mapOf(
                partitionTwo to 16L,
                partitionOne to 16L
            )
        )
    }

    @Test
    fun `processRecords will not commit the records if there was an error`() {
        val partition = mock<Partition> {
            on { getRecordsFrom(any(), any()) } doReturn (1..10).map {
                RecordMetadata(it.toLong(), Record("topic", it, it), 2)
            }
        }
        whenever(group.isConsuming(consumer))
            .thenReturn(true)
            .thenReturn(false)
        loop.addPartitions(mapOf(partition to 1004L))
        whenever(consumer.handleRecords(any())).doThrow(RuntimeException(""))

        loop.run()

        verify(group, never()).commit(
            any()
        )
    }

    @Test
    fun `processRecords will not commit when state is not auto commit`() {
        val consumer = mock<Consumer> {
            on { commitStrategy } doReturn CommitStrategy.NO_COMMIT
            on { handleRecords(any()) } doAnswer {
                records.addAll(it.getArgument(0) as Collection<RecordMetadata>)
                Unit
            }
        }

        val loop = ConsumptionLoop(consumer, group)

        val partitionTwoRecords = (10..15).map {
            RecordMetadata(it.toLong(), Record("topic", it, it), 2)
        }
        val partitionOneRecords = (10..15).map {
            RecordMetadata(it.toLong(), Record("topic", it, it), 2)
        }
        val partitionOne = mock<Partition> {
            on { getRecordsFrom(any(), any()) } doReturn partitionOneRecords
        }
        val partitionTwo = mock<Partition> {
            on { getRecordsFrom(any(), any()) } doReturn partitionTwoRecords
        }
        whenever(group.isConsuming(consumer))
            .thenReturn(true)
            .thenReturn(false)
        loop.addPartitions(
            mapOf(
                partitionOne to 1004L,
                partitionTwo to 1001L
            )
        )

        loop.run()

        verify(group, never()).commit(any())
    }

    @Test
    fun `processRecords will keep it's own records when state is not auto commit`() {
        whenever(group.pollSizePerPartition).doReturn(10)
        val consumer = mock<Consumer> {
            on { commitStrategy } doReturn CommitStrategy.NO_COMMIT
            on { handleRecords(any()) } doAnswer {
                records.addAll(it.getArgument(0) as Collection<RecordMetadata>)
                Unit
            }
        }

        val loop = ConsumptionLoop(consumer, group)

        val partitionTwoRecords = (10..15).map {
            RecordMetadata(it.toLong(), Record("topic", it, it), 2)
        }
        val partitionOneRecords = (10..15).map {
            RecordMetadata(it.toLong(), Record("topic", it, it), 2)
        }
        val partitionOneSecondRecords = (16..20).map {
            RecordMetadata(it.toLong(), Record("topic", it, it), 2)
        }
        val partitionOne = mock<Partition> {
            on { getRecordsFrom(0, 10) } doReturn partitionOneRecords
            on { getRecordsFrom(16, 10) } doReturn partitionOneSecondRecords
        }
        val partitionTwo = mock<Partition> {
            on { getRecordsFrom(0, 10) } doReturn partitionTwoRecords
            on { getRecordsFrom(16, 10) } doReturn emptyList()
        }
        whenever(group.isConsuming(consumer))
            .thenReturn(true)
            .thenReturn(true)
            .thenReturn(false)
        loop.addPartitions(
            mapOf(
                partitionOne to 0L,
                partitionTwo to 0L
            )
        )

        loop.run()

        verify(partitionOne, times(1)).getRecordsFrom(16, 10)
    }

    @Test
    fun `run will wait for phase change is there are no records`() {
        whenever(group.currentPhase())
            .thenReturn(5)
            .thenReturn(6)
        whenever(group.isConsuming(consumer))
            .thenReturn(true)
            .thenReturn(true)
            .thenReturn(false)

        loop.run()

        verify(group).waitForPhaseChange(5)
        verify(group).waitForPhaseChange(6)
    }
}
