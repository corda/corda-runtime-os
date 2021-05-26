package net.corda.messaging.kafka.subscription

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
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.stubs.StubDurableProcessor
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_POLL_AND_PROCESS_RETRIES
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messaging.kafka.properties.PublisherConfigProperties
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import net.corda.messaging.kafka.subscription.producer.builder.SubscriptionProducerBuilder
import net.corda.messaging.kafka.subscription.producer.wrapper.CordaKafkaProducer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class KafkaDurableSubscriptionImplTest {
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
        .withValue(PublisherConfigProperties.PUBLISHER_CLIENT_ID, ConfigValueFactory.fromAnyRef("clientId"))
    private val consumerBuilder: ConsumerBuilder<String, ByteBuffer> = mock()
    private val producerBuilder: SubscriptionProducerBuilder = mock()
    private val mockCordaConsumer: CordaKafkaConsumer<String, ByteBuffer> = mock()
    private val mockCordaProducer: CordaKafkaProducer = mock()
    private val mockConsumerRecords =
        generateMockConsumerRecordList(mockRecordCount, "topic", 1)
            .map { ConsumerRecordAndMeta("", it) }
            .toList()
    private var pollInvocationCount : Int = 0
    private var builderInvocationCount : Int = 0
    private lateinit var kafkaPubSubSubscriptionImpl: KafkaDurableSubscriptionImpl<String, ByteBuffer>
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
        doReturn(mockCordaConsumer).whenever(consumerBuilder).createDurableConsumer(any(), any())
        doReturn(mockCordaProducer).whenever(producerBuilder).createProducer(anyOrNull())
    }

    /**
     * Test processor is executed when a new record is added to the consumer.
     */
    @Test
    fun testDurableSubscription() {
        kafkaPubSubSubscriptionImpl = KafkaDurableSubscriptionImpl(subscriptionConfig, config, consumerBuilder, producerBuilder, processor)
        kafkaPubSubSubscriptionImpl.start()

        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        kafkaPubSubSubscriptionImpl.stop()
        verify(consumerBuilder, times(1)).createDurableConsumer(any(), any())
        verify(producerBuilder, times(1)).createProducer(anyOrNull())
        verify(mockCordaProducer, times(mockRecordCount.toInt())).beginTransaction()
        verify(mockCordaProducer, times(mockRecordCount.toInt())).sendRecords(any())
        verify(mockCordaProducer, times(mockRecordCount.toInt())).sendOffsetsToTransaction()
        verify(mockCordaProducer, times(mockRecordCount.toInt())).tryCommitTransaction()
    }

    /**
     * Check that the exceptions thrown during building exits correctly
     */
    @Test
    fun testFatalExceptionConsumerBuild() {
        whenever(consumerBuilder.createDurableConsumer(any(), any())).thenThrow(CordaMessageAPIFatalException("Fatal Error", Exception()))

        kafkaPubSubSubscriptionImpl = KafkaDurableSubscriptionImpl(subscriptionConfig, config, consumerBuilder, producerBuilder,  processor)

        kafkaPubSubSubscriptionImpl.start()
        while (kafkaPubSubSubscriptionImpl.isRunning) {}

        verify(mockCordaConsumer, times(0)).poll()
        verify(consumerBuilder, times(1)).createDurableConsumer(any(), any())
        verify(producerBuilder, times(0)).createProducer(anyOrNull())
        assertThat(latch.count).isEqualTo(mockRecordCount)
    }

    /**
     * Check that the exceptions thrown during building exits correctly
     */
    @Test
    fun testFatalExceptionProducerBuild() {
        whenever(producerBuilder.createProducer(anyOrNull())).thenThrow(CordaMessageAPIFatalException("Fatal Error", Exception()))

        kafkaPubSubSubscriptionImpl = KafkaDurableSubscriptionImpl(subscriptionConfig, config, consumerBuilder, producerBuilder,  processor)

        kafkaPubSubSubscriptionImpl.start()
        while (kafkaPubSubSubscriptionImpl.isRunning) {}

        verify(mockCordaConsumer, times(0)).poll()
        verify(consumerBuilder, times(1)).createDurableConsumer(any(), any())
        verify(producerBuilder, times(1)).createProducer(anyOrNull())
        verify(mockCordaProducer, times(0)).beginTransaction()
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
        }.whenever(consumerBuilder).createDurableConsumer(any(), any())
        whenever(mockCordaConsumer.poll()).thenThrow(CordaMessageAPIIntermittentException("Error", Exception()))

        kafkaPubSubSubscriptionImpl = KafkaDurableSubscriptionImpl(subscriptionConfig, config, consumerBuilder, producerBuilder,  processor)

        kafkaPubSubSubscriptionImpl.start()
        while (kafkaPubSubSubscriptionImpl.isRunning) {}

        assertThat(latch.count).isEqualTo(mockRecordCount)
        verify(consumerBuilder, times(2)).createDurableConsumer(any(), any())
        verify(producerBuilder, times(1)).createProducer(anyOrNull())
        verify(mockCordaProducer, times(0)).beginTransaction()
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
        }.whenever(consumerBuilder).createDurableConsumer(any(), any())

        latch = CountDownLatch(consumerPollAndProcessRetriesCount)
        processor = StubDurableProcessor(latch, CordaMessageAPIIntermittentException(""))
        doReturn(mockConsumerRecords).whenever(mockCordaConsumer).poll()

        kafkaPubSubSubscriptionImpl = KafkaDurableSubscriptionImpl(subscriptionConfig, config, consumerBuilder, producerBuilder,
            processor)

        kafkaPubSubSubscriptionImpl.start()
        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount+1)).poll()
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount)).resetToLastCommittedPositions(any())
        verify(consumerBuilder, times(2)).createDurableConsumer(any(), any())
        verify(producerBuilder, times(1)).createProducer(anyOrNull())
        verify(mockCordaProducer, times(consumerPollAndProcessRetriesCount+1)).beginTransaction()
        verify(mockCordaProducer, times(0)).sendRecords(any())
        verify(mockCordaProducer, times(0)).sendOffsetsToTransaction()
        verify(mockCordaProducer, times(0)).tryCommitTransaction()
    }

    @Test
    fun testFatalExceptionDuringTransaction() {
        latch = CountDownLatch(consumerPollAndProcessRetriesCount)
        processor = StubDurableProcessor(latch, CordaMessageAPIFatalException(""))

        kafkaPubSubSubscriptionImpl = KafkaDurableSubscriptionImpl(subscriptionConfig, config, consumerBuilder, producerBuilder,
            processor)

        kafkaPubSubSubscriptionImpl.start()
        while (kafkaPubSubSubscriptionImpl.isRunning) {}

        verify(mockCordaConsumer, times(0)).resetToLastCommittedPositions(any())
        verify(mockCordaConsumer, times(1)).poll()
        verify(consumerBuilder, times(1)).createDurableConsumer(any(), any())
        verify(producerBuilder, times(1)).createProducer(anyOrNull())
        verify(mockCordaProducer, times(1)).beginTransaction()
        verify(mockCordaProducer, times(0)).sendRecords(any())
        verify(mockCordaProducer, times(0)).sendOffsetsToTransaction()
        verify(mockCordaProducer, times(0)).tryCommitTransaction()
    }
}