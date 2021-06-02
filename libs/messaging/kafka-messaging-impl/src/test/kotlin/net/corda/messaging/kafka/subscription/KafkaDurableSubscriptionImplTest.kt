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
import net.corda.messaging.kafka.producer.builder.ProducerBuilder
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_POLL_AND_PROCESS_RETRIES
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messaging.kafka.properties.PublisherConfigProperties
import net.corda.messaging.kafka.stubs.StubDurableProcessor
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
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
    private val producerBuilder: ProducerBuilder = mock()
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
    private lateinit var pollInvocationLatch: CountDownLatch
    private lateinit var eventsLatch: CountDownLatch

    @BeforeEach
    fun setup() {
        pollInvocationLatch = CountDownLatch(1)
        eventsLatch = CountDownLatch(mockRecordCount.toInt())
        processor = StubDurableProcessor(pollInvocationLatch, eventsLatch)

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
        doReturn(mockCordaProducer).whenever(producerBuilder).createProducer()
    }

    /**
     * Test processor is executed when a new record is added to the consumer.
     */
    @Test
    fun testDurableSubscription() {
        kafkaPubSubSubscriptionImpl = KafkaDurableSubscriptionImpl(subscriptionConfig, config, consumerBuilder, producerBuilder, processor)
        kafkaPubSubSubscriptionImpl.start()

        eventsLatch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        kafkaPubSubSubscriptionImpl.stop()
        verify(consumerBuilder, times(1)).createDurableConsumer(any(), any())
        verify(producerBuilder, times(1)).createProducer()
        verify(mockCordaProducer, times(1)).beginTransaction()
        verify(mockCordaProducer, times(1)).sendRecords(any())
        verify(mockCordaProducer, times(1)).sendOffsetsToTransaction(any())
        verify(mockCordaProducer, times(1)).tryCommitTransaction()
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
        verify(producerBuilder, times(0)).createProducer()
        assertThat(eventsLatch.count).isEqualTo(mockRecordCount)
    }

    /**
     * Check that the exceptions thrown during building exits correctly
     */
    @Test
    fun testFatalExceptionProducerBuild() {
        whenever(producerBuilder.createProducer()).thenThrow(CordaMessageAPIFatalException("Fatal Error", Exception()))

        kafkaPubSubSubscriptionImpl = KafkaDurableSubscriptionImpl(subscriptionConfig, config, consumerBuilder, producerBuilder,  processor)

        kafkaPubSubSubscriptionImpl.start()
        while (kafkaPubSubSubscriptionImpl.isRunning) {}

        verify(mockCordaConsumer, times(0)).poll()
        verify(consumerBuilder, times(1)).createDurableConsumer(any(), any())
        verify(producerBuilder, times(1)).createProducer()
        verify(mockCordaProducer, times(0)).beginTransaction()
        assertThat(eventsLatch.count).isEqualTo(mockRecordCount)
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

        assertThat(eventsLatch.count).isEqualTo(mockRecordCount)
        verify(consumerBuilder, times(2)).createDurableConsumer(any(), any())
        verify(producerBuilder, times(1)).createProducer()
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

        pollInvocationLatch = CountDownLatch(consumerPollAndProcessRetriesCount)
        processor = StubDurableProcessor(pollInvocationLatch, eventsLatch, CordaMessageAPIIntermittentException(""))
        doReturn(mockConsumerRecords).whenever(mockCordaConsumer).poll()

        kafkaPubSubSubscriptionImpl = KafkaDurableSubscriptionImpl(subscriptionConfig, config, consumerBuilder, producerBuilder,
            processor)

        kafkaPubSubSubscriptionImpl.start()
        pollInvocationLatch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount+1)).poll()
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount)).resetToLastCommittedPositions(any())
        verify(consumerBuilder, times(2)).createDurableConsumer(any(), any())
        verify(producerBuilder, times(1)).createProducer()
        verify(mockCordaProducer, times(consumerPollAndProcessRetriesCount+1)).beginTransaction()
        verify(mockCordaProducer, times(0)).sendRecords(any())
        verify(mockCordaProducer, times(0)).sendOffsetsToTransaction(any())
        verify(mockCordaProducer, times(0)).tryCommitTransaction()
    }

    @Test
    fun testFatalExceptionDuringTransaction() {
        pollInvocationLatch = CountDownLatch(consumerPollAndProcessRetriesCount)
        processor = StubDurableProcessor(pollInvocationLatch, eventsLatch, CordaMessageAPIFatalException(""))

        kafkaPubSubSubscriptionImpl = KafkaDurableSubscriptionImpl(subscriptionConfig, config, consumerBuilder, producerBuilder,
            processor)

        kafkaPubSubSubscriptionImpl.start()
        while (kafkaPubSubSubscriptionImpl.isRunning) {}

        verify(mockCordaConsumer, times(0)).resetToLastCommittedPositions(any())
        verify(mockCordaConsumer, times(1)).poll()
        verify(consumerBuilder, times(1)).createDurableConsumer(any(), any())
        verify(producerBuilder, times(1)).createProducer()
        verify(mockCordaProducer, times(1)).beginTransaction()
        verify(mockCordaProducer, times(0)).sendRecords(any())
        verify(mockCordaProducer, times(0)).sendOffsetsToTransaction(any())
        verify(mockCordaProducer, times(0)).tryCommitTransaction()
    }
}