package net.corda.messaging.emulation.subscription.pubsub

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.atLeast
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.properties.InMemProperties.Companion.CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messaging.emulation.properties.InMemProperties.Companion.TOPICS_MAX_SIZE
import net.corda.messaging.emulation.properties.InMemProperties.Companion.TOPICS_POLL_SIZE
import net.corda.messaging.kafka.stubs.StubProcessor
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory.Companion.EVENT_TOPIC
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory.Companion.GROUP_NAME
import net.corda.messaging.emulation.topic.model.RecordMetadata
import net.corda.messaging.emulation.topic.service.TopicService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PubSubSubscriptionTest {
    private lateinit var subscription : PubSubSubscription<String, ByteBuffer>
    private lateinit var latch: CountDownLatch
    private lateinit var processor : StubProcessor
    private var executorService: ExecutorService? = null
    private val topic = "helloworld"
    private val topicService: TopicService = mock()
    private val record = Record(topic, "key1", ByteBuffer.wrap("value1".toByteArray()))
    private val records = listOf(
        RecordMetadata(1, record),
        RecordMetadata(2, record),
        RecordMetadata(3, record),
        RecordMetadata(4, record),
        RecordMetadata(5, record))
    private val mockRecordCount = records.size
    private val latchTimeout = 30L
    private val config = ConfigFactory.empty()
        .withValue(TOPICS_MAX_SIZE, ConfigValueFactory.fromAnyRef(5))
        .withValue(TOPICS_POLL_SIZE, ConfigValueFactory.fromAnyRef(5))
        .withValue(EVENT_TOPIC, ConfigValueFactory.fromAnyRef(topic))
        .withValue(GROUP_NAME, ConfigValueFactory.fromAnyRef("group1"))
        .withValue(CONSUMER_THREAD_STOP_TIMEOUT, ConfigValueFactory.fromAnyRef(100))

    @BeforeEach
    fun setup() {
        doReturn(records).whenever(topicService).getRecords(any(), any(), any(), any())
        latch = CountDownLatch(mockRecordCount)
        processor = StubProcessor(latch)
        subscription = PubSubSubscription(config, processor, executorService, topicService)
    }

    @Test
    fun testSubscription() {
        subscription.start()

        latch.await(latchTimeout, TimeUnit.SECONDS)

        subscription.stop()
        verify(topicService, atLeast(1)).getRecords(any(), any(), any(), any())
    }

    @Test
    fun testProcessorFail() {
        processor = StubProcessor(latch, Exception())
        subscription = PubSubSubscription(config, processor, executorService, topicService)

        subscription.start()

        latch.await(latchTimeout, TimeUnit.SECONDS)

        subscription.stop()
        verify(topicService, atLeast(1)).getRecords( any(), any(), any(), any())
    }

    @Test
    fun testSubscriptionExecutor() {
        executorService = Executors.newFixedThreadPool(1)
        subscription = PubSubSubscription(config, processor, executorService, topicService)

        subscription.start()

        latch.await(latchTimeout, TimeUnit.SECONDS)

        subscription.stop()
        verify(topicService, atLeast(1)).getRecords( any(), any(), any(), any())
    }
}