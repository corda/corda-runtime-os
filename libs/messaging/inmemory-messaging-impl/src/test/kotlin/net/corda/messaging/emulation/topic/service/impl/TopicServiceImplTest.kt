package net.corda.messaging.emulation.topic.service.impl

import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.properties.InMemoryConfiguration
import net.corda.messaging.emulation.topic.model.ConsumptionThread
import net.corda.messaging.emulation.topic.model.Topic
import net.corda.messaging.emulation.topic.model.Topics
import net.corda.messaging.emulation.topic.model.PartitionsWriteLock
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

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
    fun `subscribe will start the thread`() {
        impl.subscribe(mock())

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
    fun `handleAllRecords send the handler to the correct topic`() {
        impl.handleAllRecords("topic.1", mock())

        verify(topicOne).handleAllRecords(any())
    }
}
