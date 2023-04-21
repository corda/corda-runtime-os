package net.corda.messaging.emulation.topic.service.impl

import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.properties.InMemoryConfiguration
import net.corda.messaging.emulation.topic.model.Consumer
import net.corda.messaging.emulation.topic.model.ConsumptionThread
import net.corda.messaging.emulation.topic.model.PartitionsWriteLock
import net.corda.messaging.emulation.topic.model.Topic
import net.corda.messaging.emulation.topic.model.Topics
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TopicServiceImplTest {
    private val thread = mock<ConsumptionThread>()
    private val config = mock<InMemoryConfiguration>()
    private val topicOne = mock<Topic>()
    private val topicTwo = mock<Topic>()
    private val writeLock = mock<PartitionsWriteLock> {
        on { write(any()) } doAnswer {
            val function = it.getArgument(0) as () -> Unit
            function()
        }
    }
    private val topics = mock<Topics> {
        on { createConsumption(any()) } doReturn thread
        on { getTopic("topic.1") } doReturn topicOne
        on { getTopic("topic.2") } doReturn topicTwo
        on { getWriteLock(any()) } doReturn writeLock
        on { getWriteLock(any(), any()) } doReturn writeLock
    }
    private val impl = TopicServiceImpl(config, topics)

    @Test
    fun `createConsumption will start the thread`() {
        impl.createConsumption(mock())

        verify(thread).start()
    }

    @Test
    fun `addRecords will add the records to the correct topic`() {
        val records = (1..10).map {
            Record("topic.${it % 2 + 1}", it, it + 3)
        }
        impl.addRecords(records)

        verify(topicOne).addRecord(Record("topic.1", 2, 5))
        verify(topicOne).addRecord(Record("topic.1", 4, 7))
        verify(topicTwo).addRecord(Record("topic.2", 3, 6))
        verify(topicTwo).addRecord(Record("topic.2", 5, 8))
    }

    @Test
    fun `addRecords will lock the partitions`() {
        val records = (1..10).map {
            Record("topic.${it % 2 + 1}", it, it + 3)
        }
        impl.addRecords(records)

        verify(writeLock).write(any())
    }

    @Test
    fun `addRecords will wake up the topic consumers`() {
        val records = (1..10).map {
            Record("topic.${it % 2 + 1}", it, it + 3)
        }
        impl.addRecords(records)

        verify(topicOne).wakeUpConsumers()
        verify(topicTwo).wakeUpConsumers()
    }

    @Test
    fun `addRecordsToPartition will lock the partitions`() {
        val records = (1..10).map {
            Record("topic.${it % 2 + 1}", it, it + 3)
        }
        impl.addRecordsToPartition(records, 1)

        verify(writeLock).write(any())
    }

    @Test
    fun `addRecordsToPartition will add the records to the correct topic`() {
        val records = (1..10).map {
            Record("topic.${it % 2 + 1}", it, it + 3)
        }
        impl.addRecordsToPartition(records, 12)

        verify(topicOne).addRecordToPartition(Record("topic.1", 2, 5), 12)
        verify(topicOne).addRecordToPartition(Record("topic.1", 4, 7), 12)
        verify(topicTwo).addRecordToPartition(Record("topic.2", 3, 6), 12)
        verify(topicTwo).addRecordToPartition(Record("topic.2", 5, 8), 12)
    }

    @Test
    fun `addRecordsToPartition will wake up the consumers`() {
        val records = (1..10).map {
            Record("topic.${it % 2 + 1}", it, it + 3)
        }
        impl.addRecordsToPartition(records, 12)

        verify(topicOne).wakeUpConsumers()
        verify(topicTwo).wakeUpConsumers()
    }

    @Test
    fun `getLatestOffsets send the handler to the correct topic`() {
        whenever(topics.getLatestOffsets(any())).doReturn(mapOf(1 to 3))

        val offsets = impl.getLatestOffsets("topic.1")

        assertThat(offsets).isEqualTo(mapOf(1 to 3L))
    }

    @Test
    fun `manualAssignPartitions assign partitions to the correct topic`() {
        val consumer = mock<Consumer> {
            on { topicName } doReturn "topic.1"
        }
        impl.manualAssignPartitions(consumer, listOf(1, 2, 3))

        verify(topicOne).assignPartition(consumer, listOf(1, 2, 3))
    }

    @Test
    fun `manualUnAssignPartitions un assign partitions to the correct topic`() {
        val consumer = mock<Consumer> {
            on { topicName } doReturn "topic.1"
        }
        impl.manualUnAssignPartitions(consumer, listOf(1, 2, 3))

        verify(topicOne).unAssignPartition(consumer, listOf(1, 2, 3))
    }
}
