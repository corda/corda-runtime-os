package net.corda.messaging.impl.topic.service.impl

import net.corda.messaging.api.records.Record
import net.corda.messaging.impl.topic.model.OffsetStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread

class TopicServiceImplTest {
    private val topic = "helloworld"
    private val consumerGroup = "consumerGroup1"
    private val dummyRecord = Record(topic, "key", "value")
    private lateinit var topicService : TopicServiceImpl

    @BeforeEach
    fun setup() {
        topicService = TopicServiceImpl()
    }

    @Test
    fun testSubscribeEarliestAndGetRecords() {
        topicService.addRecords(listOf(dummyRecord, dummyRecord, dummyRecord))
        topicService.subscribe(topic, consumerGroup, OffsetStrategy.EARLIEST)
        assertThat(topicService.getRecords(topic, consumerGroup, 5).size).isEqualTo(3)
        topicService.addRecords(listOf(dummyRecord, dummyRecord))
        assertThat(topicService.getRecords(topic, consumerGroup, 5).size).isEqualTo(2)
    }

    @Test
    fun testOldestRecordRemoved() {
        topicService.subscribe(topic, consumerGroup, OffsetStrategy.EARLIEST)
        topicService.addRecords(listOf(dummyRecord, dummyRecord, dummyRecord, dummyRecord, dummyRecord, dummyRecord))
        assertThat(topicService.getRecords(topic, consumerGroup, 5).size).isEqualTo(5)
        assertThat(topicService.getRecords(topic, consumerGroup, 5)).isEmpty()
    }

    @Test
    fun testSubscribeLatestAndGetRecords() {
        topicService.addRecords(listOf(dummyRecord, dummyRecord, dummyRecord))
        topicService.subscribe(topic, consumerGroup, OffsetStrategy.LATEST)
        assertThat(topicService.getRecords(topic, consumerGroup, 5)).isEmpty()

        topicService.addRecords(listOf(dummyRecord))
        assertThat(topicService.getRecords(topic, consumerGroup, 5).size).isEqualTo(1)
    }

    @Test
    fun testSubscribeUnsubscribe() {
        topicService.addRecords(listOf(dummyRecord, dummyRecord, dummyRecord))
        topicService.subscribe(topic, consumerGroup, OffsetStrategy.EARLIEST)
        topicService.unsubscribe(topic, consumerGroup)
        assertThat(topicService.getRecords(topic, consumerGroup, 5)).isEmpty()
    }

    @Test
    fun testAddRecordsMultipleTopics() {
        val recordTopic1 = Record("topic1", "key1", "value1")
        val recordTopic2 = Record("topic2", "key2", "value2")
        val recordTopic3 = Record("topic3", "key3", "value3")
        val recordTopic4 = Record("topic4", "key4", "value4")
        topicService.addRecords(listOf(recordTopic1, recordTopic2, recordTopic3, recordTopic4))
        topicService.subscribe("topic1", consumerGroup, OffsetStrategy.EARLIEST)
        topicService.subscribe("topic2", consumerGroup, OffsetStrategy.EARLIEST)
        topicService.subscribe("topic3", consumerGroup, OffsetStrategy.EARLIEST)
        topicService.subscribe("topic4", consumerGroup, OffsetStrategy.EARLIEST)
        assertThat(topicService.getRecords("topic1", consumerGroup, 5).size).isEqualTo(1)
        assertThat(topicService.getRecords("topic2", consumerGroup, 5).size).isEqualTo(1)
        assertThat(topicService.getRecords("topic3", consumerGroup, 5).size).isEqualTo(1)
        assertThat(topicService.getRecords("topic4", consumerGroup, 5).size).isEqualTo(1)
    }


    /**
     * Verify locking procedures execute correctly and do not cause a deadlock
     */
    @Test
    fun testAddAndGetRecordsMultipleTopics() {
        val recordTopic1 = Record("topic1", "key1", "value1")
        val recordTopic2 = Record("topic2", "key2", "value2")
        val recordTopic3 = Record("topic3", "key3", "value3")
        val recordTopic4 = Record("topic4", "key4", "value4")
        val recordTopic5 = Record("topic5", "key5", "value5")
        val records = listOf(recordTopic1, recordTopic2, recordTopic3, recordTopic4, recordTopic5)

        val addRecordsThread = thread(
            start = false,
            isDaemon = true,
            contextClassLoader = null,
            name = "thread1",
            priority = -1
        ) {
            repeat(10000) {
                topicService.addRecords(records)
            }
        }

        val addRecordsReverseThread = thread(
            start = false,
            isDaemon = true,
            contextClassLoader = null,
            name = "thread2",
            priority = -1
        ) {
            repeat(10000) {
                topicService.addRecords(records.reversed())
            }
        }

        val subscribeThread = thread(
            start = false,
            isDaemon = true,
            contextClassLoader = null,
            name = "thread1",
            priority = -1
        ) {
            for (i in 0 until 10000) {
                topicService.subscribe("topic$i", "group", OffsetStrategy.EARLIEST)
            }
        }

        addRecordsThread.start()
        addRecordsReverseThread.start()
        subscribeThread.start()

        topicService.subscribe("topic1", consumerGroup, OffsetStrategy.EARLIEST)
        var recordsRetrievedOffset = 0L
        while (recordsRetrievedOffset < 10000) {
            val recordsRetrieved = topicService.getRecords("topic1", consumerGroup, 100)
            if (recordsRetrieved.isNotEmpty()) {
                recordsRetrievedOffset = recordsRetrieved.last().offset
            }
        }

        addRecordsThread.join()
        addRecordsReverseThread.join()
        subscribeThread.join()
    }

    @Test
    fun testSubscribeAndGetNoRecords() {
        topicService.subscribe(topic, consumerGroup, OffsetStrategy.EARLIEST)
        assertThat(topicService.getRecords(topic, consumerGroup, 100)).isEmpty()
    }

    @Test
    fun testGetRecordsWhenNotSubscribed() {
        topicService.addRecords(listOf(dummyRecord, dummyRecord, dummyRecord))
        assertThat(topicService.getRecords(topic, consumerGroup, 100)).isEmpty()
    }

    @Test
    fun testGetRecordsWhenNoOffsetCommitted() {
        topicService.subscribe(topic, consumerGroup, OffsetStrategy.EARLIEST)
        topicService.addRecords(listOf(dummyRecord, dummyRecord, dummyRecord))
        assertThat(topicService.getRecords(topic, consumerGroup, 100, false).size).isEqualTo(3)
        assertThat(topicService.getRecords(topic, consumerGroup, 100, false).size).isEqualTo(3)
    }
}
