package net.corda.messaging.kafka.subscription

import com.typesafe.config.Config
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.CONSUMER_POLL_AND_PROCESS_RETRIES
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_DURABLE
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.createStandardTestConfig
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.generateMockCordaConsumerRecordList
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.stubs.StubEventLogProcessor
import net.corda.messaging.subscription.EventLogSubscriptionImpl
import net.corda.messaging.subscription.consumer.builder.CordaConsumerBuilder
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

class EventLogSubscriptionImplTest {
    private companion object {
        private const val TEST_TIMEOUT_SECONDS = 3L
    }

    private var mockRecordCount = 5L
    private val config: Config = createStandardTestConfig().getConfig(PATTERN_DURABLE)
    private val consumerPollAndProcessRetriesCount = config.getInt(CONSUMER_POLL_AND_PROCESS_RETRIES)
    private val cordaConsumerBuilder: CordaConsumerBuilder = mock()
    private val cordaProducerBuilder: CordaProducerBuilder = mock()
    private val mockCordaConsumer: CordaConsumer<String, ByteBuffer> = mock()
    private val mockCordaProducer: CordaProducer = mock()
    private val mockConsumerRecords = generateMockCordaConsumerRecordList(mockRecordCount, "topic", 1)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock()
    private val lifecycleCoordinator: LifecycleCoordinator = mock()
    private var pollInvocationCount : Int = 0
    private var builderInvocationCount : Int = 0
    private lateinit var kafkaEventLogSubscription: EventLogSubscriptionImpl<String, ByteBuffer>
    private lateinit var processor: StubEventLogProcessor<String, ByteBuffer>
    private lateinit var pollInvocationLatch: CountDownLatch
    private lateinit var eventsLatch: CountDownLatch

    @BeforeEach
    fun setup() {
        pollInvocationLatch = CountDownLatch(1)
        eventsLatch = CountDownLatch(mockRecordCount.toInt())
        processor = StubEventLogProcessor(
            pollInvocationLatch,
            eventsLatch,
            null,
            String::class.java,
            ByteBuffer::class.java
        )

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
        doReturn(mockCordaConsumer).whenever(cordaConsumerBuilder).createDurableConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any(),
            anyOrNull()
        )
        doReturn(mockCordaProducer).whenever(cordaProducerBuilder).createProducer(any())
        doReturn(lifecycleCoordinator).`when`(lifecycleCoordinatorFactory).createCoordinator(any(), any())
    }

    /**
     * Test processor is executed when a new record is added to the consumer.
     */
    @Test
    fun testSubscription() {
        kafkaEventLogSubscription = EventLogSubscriptionImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            processor,
            null,
            lifecycleCoordinatorFactory
        )
        kafkaEventLogSubscription.start()

        eventsLatch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        kafkaEventLogSubscription.stop()
        verify(cordaConsumerBuilder, times(1)).createDurableConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any(),
            anyOrNull()
        )
        verify(cordaProducerBuilder, times(1)).createProducer(any())
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
        whenever(cordaConsumerBuilder.createDurableConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any(),
            anyOrNull()
        ))
            .thenThrow(CordaMessageAPIFatalException("Fatal Error", Exception()))

        kafkaEventLogSubscription = EventLogSubscriptionImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            processor,
            null,
            lifecycleCoordinatorFactory
        )

        kafkaEventLogSubscription.start()
        while (kafkaEventLogSubscription.isRunning) { Thread.sleep(10) }

        verify(mockCordaConsumer, times(0)).poll()
        verify(cordaConsumerBuilder, times(1)).createDurableConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any(),
            anyOrNull()
        )
        verify(cordaProducerBuilder, times(1)).createProducer(any())
        assertThat(eventsLatch.count).isEqualTo(mockRecordCount)
    }

    /**
     * Check that the exceptions thrown during building exits correctly
     */
    @Test
    fun testFatalExceptionProducerBuild() {
        whenever(cordaProducerBuilder.createProducer(any())).thenThrow(CordaMessageAPIFatalException("Fatal Error", Exception()))

        kafkaEventLogSubscription = EventLogSubscriptionImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            processor,
            null,
            lifecycleCoordinatorFactory
        )

        kafkaEventLogSubscription.start()
        while (kafkaEventLogSubscription.isRunning) { Thread.sleep(10) }

        verify(mockCordaConsumer, times(0)).poll()
        verify(cordaConsumerBuilder, times(1)).createDurableConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any(),
            anyOrNull()
        )
        verify(cordaProducerBuilder, times(1)).createProducer(any())
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
        }.whenever(cordaConsumerBuilder).createDurableConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any(),
            anyOrNull()
        )
        whenever(mockCordaConsumer.poll()).thenThrow(CordaMessageAPIIntermittentException("Error", Exception()))

        kafkaEventLogSubscription = EventLogSubscriptionImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            processor,
            null,
            lifecycleCoordinatorFactory
        )

        kafkaEventLogSubscription.start()
        while (kafkaEventLogSubscription.isRunning) { Thread.sleep(10) }

        assertThat(eventsLatch.count).isEqualTo(mockRecordCount)
        verify(cordaConsumerBuilder, times(2)).createDurableConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any(),
            anyOrNull()
        )
        verify(cordaProducerBuilder, times(1)).createProducer(any())
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
        }.whenever(cordaConsumerBuilder).createDurableConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any(),
            anyOrNull()
        )

        pollInvocationLatch = CountDownLatch(consumerPollAndProcessRetriesCount)
        processor = StubEventLogProcessor(pollInvocationLatch, eventsLatch, CordaMessageAPIIntermittentException(""),
            String::class.java, ByteBuffer::class.java)
        doReturn(mockConsumerRecords).whenever(mockCordaConsumer).poll()

        kafkaEventLogSubscription = EventLogSubscriptionImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            processor,
            null,
            lifecycleCoordinatorFactory
        )

        kafkaEventLogSubscription.start()
        while (kafkaEventLogSubscription.isRunning) { Thread.sleep(10) }

        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount+1)).poll()
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount)).resetToLastCommittedPositions(any())
        verify(cordaConsumerBuilder, times(2)).createDurableConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any(),
            anyOrNull()
        )
        verify(cordaProducerBuilder, times(1)).createProducer(any())
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

        kafkaEventLogSubscription = EventLogSubscriptionImpl(
            config,
            cordaConsumerBuilder,
            cordaProducerBuilder,
            processor,
            null,
            lifecycleCoordinatorFactory
        )

        kafkaEventLogSubscription.start()
        while (kafkaEventLogSubscription.isRunning) { Thread.sleep(10) }

        verify(mockCordaConsumer, times(0)).resetToLastCommittedPositions(any())
        verify(mockCordaConsumer, times(1)).poll()
        verify(cordaConsumerBuilder, times(1)).createDurableConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any(),
            anyOrNull()
        )
        verify(cordaProducerBuilder, times(1)).createProducer(any())
        verify(mockCordaProducer, times(1)).beginTransaction()
        verify(mockCordaProducer, times(0)).sendRecords(any())
        verify(mockCordaProducer, times(0)).sendRecordOffsetsToTransaction(any(), anyOrNull())
        verify(mockCordaProducer, times(0)).commitTransaction()
    }
}
