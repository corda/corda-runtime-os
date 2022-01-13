package net.corda.messaging.kafka.subscription

import com.typesafe.config.Config
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.kafka.producer.builder.ProducerBuilder
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.CONSUMER_POLL_AND_PROCESS_RETRIES
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_DURABLE
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.createStandardTestConfig
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.stubs.StubEventLogProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class KafkaEventLogSubscriptionImplTest {
    private companion object {
        private const val TEST_TIMEOUT_SECONDS = 3L
    }

    private var mockRecordCount = 5L
    private val config: Config = createStandardTestConfig().getConfig(PATTERN_DURABLE)
    private val consumerPollAndProcessRetriesCount = config.getInt(CONSUMER_POLL_AND_PROCESS_RETRIES)
    private val consumerBuilder: ConsumerBuilder<String, ByteBuffer> = mock()
    private val producerBuilder: ProducerBuilder = mock()
    private val mockCordaConsumer: CordaKafkaConsumer<String, ByteBuffer> = mock()
    private val mockCordaProducer: CordaKafkaProducer = mock()
    private val mockConsumerRecords = generateMockConsumerRecordList(mockRecordCount, "topic", 1)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock()
    private val lifecycleCoordinator: LifecycleCoordinator = mock()
    private var pollInvocationCount : Int = 0
    private var builderInvocationCount : Int = 0
    private lateinit var kafkaEventLogSubscription: KafkaEventLogSubscriptionImpl<String, ByteBuffer>
    private lateinit var processor: StubEventLogProcessor<String, ByteBuffer>
    private lateinit var pollInvocationLatch: CountDownLatch
    private lateinit var eventsLatch: CountDownLatch

    @BeforeEach
    fun setup() {
        pollInvocationLatch = CountDownLatch(1)
        eventsLatch = CountDownLatch(mockRecordCount.toInt())
        processor = StubEventLogProcessor(pollInvocationLatch, eventsLatch, null, String::class.java, ByteBuffer::class.java)

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
        doReturn(mockCordaConsumer).whenever(consumerBuilder).createDurableConsumer(any(), any(), any(), any(), anyOrNull())
        doReturn(mockCordaProducer).whenever(producerBuilder).createProducer(any())
        doReturn(lifecycleCoordinator).`when`(lifecycleCoordinatorFactory).createCoordinator(any(), any())
    }

    /**
     * Test processor is executed when a new record is added to the consumer.
     */
    @Test
    fun testSubscription() {
        kafkaEventLogSubscription = KafkaEventLogSubscriptionImpl(
            config,
            consumerBuilder,
            producerBuilder,
            processor,
            null,
            lifecycleCoordinatorFactory
        )
        kafkaEventLogSubscription.start()

        eventsLatch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        kafkaEventLogSubscription.stop()
        verify(consumerBuilder, times(1)).createDurableConsumer(any(), any(), any(), any(), anyOrNull())
        verify(producerBuilder, times(1)).createProducer(any())
        verify(mockCordaProducer, times(1)).beginTransaction()
        verify(mockCordaProducer, times(1)).sendRecords(any())
        verify(mockCordaProducer, times(1)).sendAllOffsetsToTransaction(any())
        verify(mockCordaProducer, times(1)).commitTransaction()
    }

    /**
     * Check that the exceptions thrown during building exits correctly
     */
    @Test
    fun testFatalExceptionConsumerBuild() {
        whenever(consumerBuilder.createDurableConsumer(any(), any(), any(), any(), anyOrNull()))
            .thenThrow(CordaMessageAPIFatalException("Fatal Error", Exception()))

        kafkaEventLogSubscription = KafkaEventLogSubscriptionImpl(
            config,
            consumerBuilder,
            producerBuilder,
            processor,
            null,
            lifecycleCoordinatorFactory
        )

        kafkaEventLogSubscription.start()
        while (kafkaEventLogSubscription.isRunning) { Thread.sleep(10) }

        verify(mockCordaConsumer, times(0)).poll()
        verify(consumerBuilder, times(1)).createDurableConsumer(any(), any(), any(), any(), anyOrNull())
        verify(producerBuilder, times(1)).createProducer(any())
        assertThat(eventsLatch.count).isEqualTo(mockRecordCount)
    }

    /**
     * Check that the exceptions thrown during building exits correctly
     */
    @Test
    fun testFatalExceptionProducerBuild() {
        whenever(producerBuilder.createProducer(any())).thenThrow(CordaMessageAPIFatalException("Fatal Error", Exception()))

        kafkaEventLogSubscription = KafkaEventLogSubscriptionImpl(
            config,
            consumerBuilder,
            producerBuilder,
            processor,
            null,
            lifecycleCoordinatorFactory
        )

        kafkaEventLogSubscription.start()
        while (kafkaEventLogSubscription.isRunning) { Thread.sleep(10) }

        verify(mockCordaConsumer, times(0)).poll()
        verify(consumerBuilder, times(0)).createDurableConsumer(any(), any(), any(), any(), anyOrNull())
        verify(producerBuilder, times(1)).createProducer(any())
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
        }.whenever(consumerBuilder).createDurableConsumer(any(), any(), any(), any(), anyOrNull())
        whenever(mockCordaConsumer.poll()).thenThrow(CordaMessageAPIIntermittentException("Error", Exception()))

        kafkaEventLogSubscription = KafkaEventLogSubscriptionImpl(
            config,
            consumerBuilder,
            producerBuilder,
            processor,
            null,
            lifecycleCoordinatorFactory
        )

        kafkaEventLogSubscription.start()
        while (kafkaEventLogSubscription.isRunning) { Thread.sleep(10) }

        assertThat(eventsLatch.count).isEqualTo(mockRecordCount)
        verify(consumerBuilder, times(2)).createDurableConsumer(any(), any(), any(), any(), anyOrNull())
        verify(producerBuilder, times(2)).createProducer(any())
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
                throw CordaMessageAPIFatalException("Consumer Create Fatal Error", Exception())
            }
        }.whenever(consumerBuilder).createDurableConsumer(any(), any(), any(), any(), anyOrNull())

        pollInvocationLatch = CountDownLatch(consumerPollAndProcessRetriesCount)
        processor = StubEventLogProcessor(pollInvocationLatch, eventsLatch, CordaMessageAPIIntermittentException(""),
            String::class.java, ByteBuffer::class.java)
        doReturn(mockConsumerRecords).whenever(mockCordaConsumer).poll()

        kafkaEventLogSubscription = KafkaEventLogSubscriptionImpl(
            config,
            consumerBuilder,
            producerBuilder,
            processor,
            null,
            lifecycleCoordinatorFactory
        )

        kafkaEventLogSubscription.start()
        while (kafkaEventLogSubscription.isRunning) { Thread.sleep(10) }

        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount+1)).poll()
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount)).resetToLastCommittedPositions(any())
        verify(consumerBuilder, times(2)).createDurableConsumer(any(), any(), any(), any(), anyOrNull())
        verify(producerBuilder, times(2)).createProducer(any())
        verify(mockCordaProducer, times(consumerPollAndProcessRetriesCount+1)).beginTransaction()
        verify(mockCordaProducer, times(0)).sendRecords(any())
        verify(mockCordaProducer, times(0)).sendAllOffsetsToTransaction(any())
        verify(mockCordaProducer, times(0)).commitTransaction()
    }

    @Test
    fun testFatalExceptionDuringTransaction() {
        pollInvocationLatch = CountDownLatch(consumerPollAndProcessRetriesCount)
        processor = StubEventLogProcessor(pollInvocationLatch, eventsLatch, CordaMessageAPIFatalException(""),
            String::class.java, ByteBuffer::class.java)

        kafkaEventLogSubscription = KafkaEventLogSubscriptionImpl(
            config,
            consumerBuilder,
            producerBuilder,
            processor,
            null,
            lifecycleCoordinatorFactory
        )

        kafkaEventLogSubscription.start()
        while (kafkaEventLogSubscription.isRunning) { Thread.sleep(10) }

        verify(mockCordaConsumer, times(0)).resetToLastCommittedPositions(any())
        verify(mockCordaConsumer, times(1)).poll()
        verify(consumerBuilder, times(1)).createDurableConsumer(any(), any(), any(), any(), anyOrNull())
        verify(producerBuilder, times(1)).createProducer(any())
        verify(mockCordaProducer, times(1)).beginTransaction()
        verify(mockCordaProducer, times(0)).sendRecords(any())
        verify(mockCordaProducer, times(0)).sendRecordOffsetsToTransaction(any(), anyOrNull())
        verify(mockCordaProducer, times(0)).commitTransaction()
    }
}
