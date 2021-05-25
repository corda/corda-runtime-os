package net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.subscription.durable

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.anyOrNull
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_POLL_AND_PROCESS_RETRIES
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.durable.KafkaDurableSubscription
import net.corda.messaging.kafka.subscription.generateMockConsumerRecordList
import net.corda.messaging.kafka.subscription.net.corda.messaging.emulation.stubs.StubDurableProcessor
import net.corda.messaging.kafka.subscription.producer.builder.SubscriptionProducerBuilder
import net.corda.messaging.kafka.subscription.producer.wrapper.CordaKafkaProducer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class KafkaDurableSubscriptionTest {
    private companion object {
        private const val TOPIC = "topic1"
        private const val TEST_TIMEOUT_SECONDS = 30L
    }

    private val subscriptionConfig: SubscriptionConfig = SubscriptionConfig("group1",  TOPIC)
    private var mockRecordCount = 5L
    private val consumerPollAndProcessRetriesCount = 2
    private val config: Config = ConfigFactory.empty()
        .withValue(CONSUMER_POLL_AND_PROCESS_RETRIES, ConfigValueFactory.fromAnyRef(consumerPollAndProcessRetriesCount))
        .withValue(CONSUMER_THREAD_STOP_TIMEOUT, ConfigValueFactory.fromAnyRef(1000))
    private val consumerBuilder: ConsumerBuilder<String, ByteBuffer> = mock()
    private val producerBuilder: SubscriptionProducerBuilder = mock()
    private val mockCordaConsumer: CordaKafkaConsumer<String, ByteBuffer> = mock()
    private val mockCordaProducer: CordaKafkaProducer = mock()
    private val mockConsumerRecords: List<ConsumerRecord<String, ByteBuffer>> = generateMockConsumerRecordList(mockRecordCount, "topic", 1)

    private var pollInvocationCount : Int = 0
    private var builderInvocationCount : Int = 0
    private lateinit var kafkaPubSubSubscription: KafkaDurableSubscription<String, ByteBuffer>
    private lateinit var processor: DurableProcessor<String, ByteBuffer>
    private lateinit var latch: CountDownLatch

    @BeforeEach
    fun setup() {
        latch = CountDownLatch(mockRecordCount.toInt())
        processor = StubDurableProcessor(latch)

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
        doReturn(mockCordaConsumer).whenever(consumerBuilder).createDurableConsumer(any())
        doReturn(mockCordaProducer).whenever(producerBuilder).createProducer(anyOrNull())
        doReturn(Record("topic", "key", "value".toByteArray())).whenever(mockCordaConsumer).getRecord(any())
    }

    /**
     * Test processor is executed when a new record is added to the consumer.
     */
    @Test
    fun testDurableSubscription() {
        kafkaPubSubSubscription = KafkaDurableSubscription(subscriptionConfig, config, consumerBuilder, producerBuilder, processor)
        kafkaPubSubSubscription.start()

        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        kafkaPubSubSubscription.stop()
        verify(consumerBuilder, times(1)).createDurableConsumer(any())
        verify(producerBuilder, times(1)).createProducer(anyOrNull())
        verify(mockCordaProducer, times(mockRecordCount.toInt())).beginTransaction()
        verify(mockCordaProducer, times(mockRecordCount.toInt())).sendRecords(any())
        verify(mockCordaProducer, times(mockRecordCount.toInt())).sendOffsetsToTransaction()
        verify(mockCordaProducer, times(mockRecordCount.toInt())).commitTransaction()
    }

    /**
     * Check that the exceptions thrown during building exits correctly
     */
    @Test
    fun testFatalExceptionConsumerBuild() {
        whenever(consumerBuilder.createDurableConsumer(any())).thenThrow(CordaMessageAPIFatalException("Fatal Error", Exception()))

        kafkaPubSubSubscription = KafkaDurableSubscription(subscriptionConfig, config, consumerBuilder, producerBuilder,  processor)

        kafkaPubSubSubscription.start()
        while (kafkaPubSubSubscription.isRunning) {}

        verify(mockCordaConsumer, times(0)).poll()
        verify(consumerBuilder, times(1)).createDurableConsumer(any())
        verify(producerBuilder, times(0)).createProducer(anyOrNull())
        assertThat(latch.count).isEqualTo(mockRecordCount)
    }

    /**
     * Check that the exceptions thrown during building exits correctly
     */
    @Test
    fun testFatalExceptionProducerBuild() {
        whenever(producerBuilder.createProducer(anyOrNull())).thenThrow(CordaMessageAPIFatalException("Fatal Error", Exception()))

        kafkaPubSubSubscription = KafkaDurableSubscription(subscriptionConfig, config, consumerBuilder, producerBuilder,  processor)

        kafkaPubSubSubscription.start()
        while (kafkaPubSubSubscription.isRunning) {}

        verify(mockCordaConsumer, times(0)).poll()
        verify(consumerBuilder, times(1)).createDurableConsumer(any())
        verify(producerBuilder, times(1)).createProducer(anyOrNull())
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
        }.whenever(consumerBuilder).createDurableConsumer(any())
        whenever(mockCordaConsumer.poll()).thenThrow(CordaMessageAPIIntermittentException("Error", Exception()))

        kafkaPubSubSubscription = KafkaDurableSubscription(subscriptionConfig, config, consumerBuilder, producerBuilder,  processor)

        kafkaPubSubSubscription.start()
        while (kafkaPubSubSubscription.isRunning) {}

        assertThat(latch.count).isEqualTo(mockRecordCount)
        verify(consumerBuilder, times(2)).createDurableConsumer(any())
        verify(producerBuilder, times(1)).createProducer(anyOrNull())
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount)).resetToLastCommittedPositions(any())
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount+1)).poll()
    }

    @Test
    fun testIntermittentExceptionDuringProcessing() {
        doAnswer {
            if (builderInvocationCount == 0) {
                builderInvocationCount++
                mockCordaConsumer
            } else {
                CordaMessageAPIFatalException("Consumer Create Fatal Error", Exception())
            }
        }.whenever(consumerBuilder).createDurableConsumer(any())

        latch = CountDownLatch(consumerPollAndProcessRetriesCount)
        processor = StubDurableProcessor(latch, CordaMessageAPIIntermittentException(""))
        doReturn(mockConsumerRecords).whenever(mockCordaConsumer).poll()

        kafkaPubSubSubscription = KafkaDurableSubscription(subscriptionConfig, config, consumerBuilder, producerBuilder,
            processor)

        kafkaPubSubSubscription.start()
        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount+1)).poll()
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount)).resetToLastCommittedPositions(any())
        verify(consumerBuilder, times(2)).createDurableConsumer(any())
        verify(producerBuilder, times(1)).createProducer(anyOrNull())
    }

    @Test
    fun testFatalExceptionDuringTransaction() {
        latch = CountDownLatch(consumerPollAndProcessRetriesCount)
        processor = StubDurableProcessor(latch, CordaMessageAPIFatalException(""))

        kafkaPubSubSubscription = KafkaDurableSubscription(subscriptionConfig, config, consumerBuilder, producerBuilder,
            processor)

        kafkaPubSubSubscription.start()
        while (kafkaPubSubSubscription.isRunning) {}

        verify(mockCordaConsumer, times(0)).resetToLastCommittedPositions(any())
        verify(mockCordaConsumer, times(1)).poll()
        verify(consumerBuilder, times(1)).createDurableConsumer(any())
        verify(producerBuilder, times(1)).createProducer(anyOrNull())
    }
}