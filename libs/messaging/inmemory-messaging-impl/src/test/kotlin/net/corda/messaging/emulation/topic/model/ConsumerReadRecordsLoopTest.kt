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

class ConsumerReadRecordsLoopTest {
    private val config = SubscriptionConfiguration(10, Duration.ofSeconds(1))
    private val records = mutableListOf<RecordMetadata>()
    private val consumer = mock<Consumer> {
        on { handleRecords(any()) } doAnswer {
            records.addAll(it.getArgument(0) as Collection<RecordMetadata>)
            Unit
        }
    }
    private val group = mock<ConsumerGroup> {
        on { subscriptionConfig } doReturn config
    }

    private val loop = ConsumerReadRecordsLoop(consumer, group)

    @Test
    fun `run will not ask for records if consumer is not subscribe`() {
        doReturn(false).whenever(group).isSubscribed(consumer)

        loop.run()

        verify(group, never()).getPartitions(any())
    }

    @Test
    fun `run will not ask for records as long as the consumer is subscribe`() {
        whenever(group.isSubscribed(consumer))
            .thenReturn(true)
            .thenReturn(true)
            .thenReturn(true)
            .thenReturn(false)

        loop.run()

        verify(group, times(3)).getPartitions(any())
    }

    @Test
    fun `readRecords will ignore the consumer if it has no partitions`() {
        whenever(group.isSubscribed(consumer))
            .thenReturn(true)
            .thenReturn(false)
        whenever(group.getPartitions(consumer))
            .thenReturn(emptyList())

        loop.run()

        verify(consumer, never()).handleRecords(any())
    }

    @Test
    fun `readRecords read the correct records`() {
        val partition = mock<Partition> {
            on { getRecordsFrom(any(), any()) } doReturn emptyList()
        }
        whenever(group.isSubscribed(consumer))
            .thenReturn(true)
            .thenReturn(false)
        whenever(group.getPartitions(consumer))
            .thenReturn(listOf(partition to 1004L))

        loop.run()

        verify(partition).getRecordsFrom(1004L, config.partitionPollSize)
    }

    @Test
    fun `readRecords wil not ignore non empty list`() {
        val recordsToSend = (1..7).map {
            RecordMetadata(it.toLong(), Record("topic", it, it), 1)
        }
        val partition = mock<Partition> {
            on { getRecordsFrom(any(), any()) } doReturn recordsToSend
        }
        whenever(group.isSubscribed(consumer))
            .thenReturn(true)
            .thenReturn(false)
        whenever(group.getPartitions(consumer))
            .thenReturn(listOf(partition to 1004L))

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
        whenever(group.isSubscribed(consumer))
            .thenReturn(true)
            .thenReturn(false)
        whenever(group.getPartitions(consumer))
            .thenReturn(
                listOf(
                    partitionOne to 1004L,
                    partitionTwo to 1001L
                )
            )

        loop.run()

        verify(group).commitRecord(
            partitionTwo, partitionTwoRecords
        )
        verify(group).commitRecord(
            partitionOne, partitionOneRecords
        )
    }

    @Test
    fun `processRecords will not commit the records if there was an error`() {
        val partitionOne = mock<Partition> {
            on { getRecordsFrom(any(), any()) } doReturn (1..10).map {
                RecordMetadata(it.toLong(), Record("topic", it, it), 2)
            }
        }
        whenever(group.isSubscribed(consumer))
            .thenReturn(true)
            .thenReturn(false)
        whenever(group.getPartitions(consumer))
            .thenReturn(
                listOf(
                    partitionOne to 1004L,
                )
            )
        whenever(consumer.handleRecords(any())).doThrow(RuntimeException(""))

        loop.run()

        verify(group, never()).commitRecord(
            any(), any()
        )
    }
}
