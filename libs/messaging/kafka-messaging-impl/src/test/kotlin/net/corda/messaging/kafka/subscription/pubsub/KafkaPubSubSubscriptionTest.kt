package net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.subscription.pubsub

import com.nhaarman.mockito_kotlin.*
import com.typesafe.config.Config
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.whenever
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_POLL_AND_PROCESS_RETRIES
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.generateMockConsumerRecordList
import net.corda.messaging.kafka.subscription.net.corda.messaging.emulation.stubs.StubProcessor
import net.corda.messaging.kafka.subscription.pubsub.KafkaPubSubSubscription
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.lang.Exception
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class KafkaPubSubSubscriptionTest {
    private companion object {
        private const val TOPIC = "topic1"
        private const val TEST_TIMEOUT_SECONDS = 30L
    }

    private val subscriptionConfig: SubscriptionConfig = SubscriptionConfig("group1",  TOPIC, 1)
    private var mockRecordCount = 5L
    private val consumerPollAndProcessRetriesCount = 2
    private val config: Config = ConfigFactory.empty()
        .withValue(CONSUMER_POLL_AND_PROCESS_RETRIES, ConfigValueFactory.fromAnyRef(consumerPollAndProcessRetriesCount))
        .withValue(CONSUMER_THREAD_STOP_TIMEOUT, ConfigValueFactory.fromAnyRef(1000))
    private val consumerBuilder: ConsumerBuilder<String, ByteBuffer> = mock()
    private val mockCordaConsumer: CordaKafkaConsumer<String, ByteBuffer> = mock()
    private val mockConsumerRecords: List<ConsumerRecord<String, ByteBuffer>> = generateMockConsumerRecordList(mockRecordCount, "topic", 1)

    private var executorService: ExecutorService? = null
    private var pollInvocationCount : Int = 0
    private var builderInvocationCount : Int = 0
    private lateinit var kafkaPubSubSubscription: KafkaPubSubSubscription<String, ByteBuffer>
    private lateinit var processor: PubSubProcessor<String, ByteBuffer>
    private lateinit var latch: CountDownLatch

    @BeforeEach
    fun setup() {
        latch = CountDownLatch(mockRecordCount.toInt())
        processor = StubProcessor(latch)

        pollInvocationCount = 0
        doAnswer{
            if (pollInvocationCount == 0) {
                pollInvocationCount++
                mockConsumerRecords
            } else {
                mutableListOf()
            }
        }.whenever(mockCordaConsumer).poll()

        builderInvocationCount = 0
        doReturn(mockCordaConsumer).whenever(consumerBuilder).createConsumer(any())
        doReturn(Record("topic", "key", "value".toByteArray())).whenever(mockCordaConsumer).getRecord(any())
    }

    /**
     * Test processor is executed when a new record is added to the consumer.
     */
    @Test
    fun testPubSubConsumer() {
        kafkaPubSubSubscription = KafkaPubSubSubscription(subscriptionConfig, config, consumerBuilder, processor, executorService)
        kafkaPubSubSubscription.start()

        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        kafkaPubSubSubscription.stop()
        verify(consumerBuilder, times(1)).createConsumer(any())
        verify(mockCordaConsumer, times(mockRecordCount.toInt())).commitSyncOffsets(any(), isNull())
    }

    /**
     * Test processor is executed when the executor is used.
     */
    @Test
    fun testPubSubConsumerWithExecutor() {
        executorService = Executors.newFixedThreadPool(1)
        kafkaPubSubSubscription = KafkaPubSubSubscription(subscriptionConfig, config, consumerBuilder, processor, executorService)

        kafkaPubSubSubscription.start()

        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        verify(consumerBuilder, times(1)).createConsumer(any())
        verify(mockCordaConsumer, times(mockRecordCount.toInt())).commitSyncOffsets(any(),isNull())
    }

    /**
     * Check that the exceptions thrown during building exits correctly
     */
    @Test
    fun testFatalExceptionConsumerBuild() {
        whenever(consumerBuilder.createConsumer(any())).thenThrow(CordaMessageAPIFatalException("Fatal Error", Exception()))

        kafkaPubSubSubscription = KafkaPubSubSubscription(subscriptionConfig, config, consumerBuilder, processor, executorService)

        kafkaPubSubSubscription.start()
        while (kafkaPubSubSubscription.isRunning) {}

        verify(mockCordaConsumer, times(0)).poll()
        verify(consumerBuilder, times(1)).createConsumer(any())
        assertThat(latch.count).isEqualTo(mockRecordCount)
    }

    /**
     * Check that the exceptions thrown during polling exits correctly
     */
    @Test
    fun testConsumerPollFailRetries() {
        doAnswer {
            if (builderInvocationCount == 0) {
                builderInvocationCount++
                mockCordaConsumer
            } else {
                CordaMessageAPIFatalException("Consumer Create Fatal Error", Exception())
            }
        }.whenever(consumerBuilder).createConsumer(any())
        whenever(mockCordaConsumer.poll()).thenThrow(CordaMessageAPIFatalException("Fatal Error", Exception()))

        kafkaPubSubSubscription = KafkaPubSubSubscription(subscriptionConfig, config, consumerBuilder, processor, executorService)

        kafkaPubSubSubscription.start()
        while (kafkaPubSubSubscription.isRunning) {}

        assertThat(latch.count).isEqualTo(mockRecordCount)
        verify(consumerBuilder, times(2)).createConsumer(any())
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount)).resetToLastCommittedPositions(any())
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount+1)).poll()
    }


    /**
     * Check that the exceptions thrown during processing exits correctly
     */
    @Test
    fun testCordaExceptionDuringProcessing() {
        doAnswer {
            if (builderInvocationCount == 0) {
                builderInvocationCount++
                mockCordaConsumer
            } else {
                CordaMessageAPIFatalException("Consumer Create Fatal Error", Exception())
            }
        }.whenever(consumerBuilder).createConsumer(any())

        latch = CountDownLatch(consumerPollAndProcessRetriesCount)
        processor = StubProcessor(latch, CordaMessageAPIFatalException("", Exception()))
        doReturn(mockConsumerRecords).whenever(mockCordaConsumer).poll()

        kafkaPubSubSubscription = KafkaPubSubSubscription(subscriptionConfig, config, consumerBuilder,
            processor, executorService)

        kafkaPubSubSubscription.start()
        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount)).resetToLastCommittedPositions(any())
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount+1)).poll()
    }

    /**
     * Check that the exceptions thrown during processing exits correctly
     */
    @Test
    fun testCordaExceptionDuringDeserialization() {
        doAnswer {
            if (builderInvocationCount == 0) {
                builderInvocationCount++
                mockCordaConsumer
            } else {
                CordaMessageAPIFatalException("Consumer Create Fatal Error", Exception())
            }
        }.whenever(consumerBuilder).createConsumer(any())
        doAnswer{
            if (pollInvocationCount == 0) {
                pollInvocationCount++
                mockConsumerRecords
            } else {
                CordaMessageAPIFatalException("Consumer Create Fatal Error", Exception())
            }
        }.whenever(mockCordaConsumer).poll()

        doThrow(CordaMessageAPIFatalException("")).whenever(mockCordaConsumer).getRecord(any())


        kafkaPubSubSubscription = KafkaPubSubSubscription(subscriptionConfig, config, consumerBuilder,
            processor, executorService)

        kafkaPubSubSubscription.start()
        while (kafkaPubSubSubscription.isRunning) {}

        verify(mockCordaConsumer, times(mockConsumerRecords.size)).commitSyncOffsets(any(), anyOrNull())
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount)).resetToLastCommittedPositions(any())
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount+2)).poll()
    }

    @Test
    fun testIOExceptionDuringProcessing() {
        doAnswer {
            if (builderInvocationCount == 0) {
                builderInvocationCount++
                mockCordaConsumer
            } else {
                CordaMessageAPIFatalException("Consumer Create Fatal Error", Exception())
            }
        }.whenever(consumerBuilder).createConsumer(any())

        latch = CountDownLatch(consumerPollAndProcessRetriesCount)
        processor = StubProcessor(latch, IOException())
        doReturn(mockConsumerRecords).whenever(mockCordaConsumer).poll()

        kafkaPubSubSubscription = KafkaPubSubSubscription(subscriptionConfig, config, consumerBuilder,
            processor, executorService)

        kafkaPubSubSubscription.start()
        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount)).resetToLastCommittedPositions(any())
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount+1)).poll()
    }
}