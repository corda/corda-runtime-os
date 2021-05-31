package net.corda.messaging.kafka.subscription

import com.nhaarman.mockito_kotlin.*
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_POLL_AND_PROCESS_RETRIES
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messaging.kafka.stubs.StubPubSubProcessor
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class KafkaPubSubSubscriptionImplTest {
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
    private val mockCordaConsumer: CordaKafkaConsumer<String, ByteBuffer> = mock()
    private val mockConsumerRecords =
        generateMockConsumerRecordList(mockRecordCount, "topic", 1)
            .map { ConsumerRecordAndMeta("", it) }
            .toList()

    private var executorService: ExecutorService? = null
    private var pollInvocationCount: Int = 0
    private var builderInvocationCount: Int = 0
    private lateinit var kafkaPubSubSubscription: KafkaPubSubSubscriptionImpl<String, ByteBuffer>
    private lateinit var processor: PubSubProcessor<String, ByteBuffer>
    private lateinit var latch: CountDownLatch

    @BeforeEach
    fun setup() {
        latch = CountDownLatch(mockRecordCount.toInt())
        processor = StubPubSubProcessor(latch)

        pollInvocationCount = 0
        doAnswer {
            if (pollInvocationCount == 0) {
                pollInvocationCount++
                mockConsumerRecords
            } else {
                mutableListOf()
            }
        }.whenever(mockCordaConsumer).poll()

        builderInvocationCount = 0
        doReturn(mockCordaConsumer).whenever(consumerBuilder).createPubSubConsumer(any(), any())
    }

    /**
     * Test processor is executed when a new record is added to the consumer.
     */
    @Test
    fun testPubSubConsumer() {
        kafkaPubSubSubscription =
            KafkaPubSubSubscriptionImpl(subscriptionConfig, config, consumerBuilder, processor, executorService)
        kafkaPubSubSubscription.start()

        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        kafkaPubSubSubscription.stop()
        assertThat(latch.count).isEqualTo(0)
        verify(consumerBuilder, times(1)).createPubSubConsumer(any(), any())
    }

    /**
     * Test processor is executed when the executor is used.
     */
    @Test
    fun testPubSubConsumerWithExecutor() {
        executorService = Executors.newFixedThreadPool(1)
        kafkaPubSubSubscription =
            KafkaPubSubSubscriptionImpl(subscriptionConfig, config, consumerBuilder, processor, executorService)

        kafkaPubSubSubscription.start()

        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertThat(latch.count).isEqualTo(0)
        verify(consumerBuilder, times(1)).createPubSubConsumer(any(), any())
    }


    /**
     * Test commitSyncOffsets is called
     */
    @Test
    fun testPubSubConsumerCommitSyncOffset() {
        doAnswer {
            if (builderInvocationCount == 0) {
                builderInvocationCount++
                mockCordaConsumer
            } else {
                CordaMessageAPIFatalException("Consumer Create Fatal Error", Exception())
            }
        }.whenever(consumerBuilder).createPubSubConsumer(any(), any())
        doReturn(mockConsumerRecords).whenever(mockCordaConsumer).poll()

        doThrow(CordaMessageAPIFatalException::class).whenever(mockCordaConsumer).commitSyncOffsets(any(), anyOrNull())
        kafkaPubSubSubscription =
            KafkaPubSubSubscriptionImpl(subscriptionConfig, config, consumerBuilder, processor, executorService)

        kafkaPubSubSubscription.start()
        while (kafkaPubSubSubscription.isRunning) { }

        assertThat(latch.count).isEqualTo(2)
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount + 1)).poll()
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount + 1)).commitSyncOffsets(any(), isNull())
        verify(consumerBuilder, times(2)).createPubSubConsumer(any(), any())
    }

    /**
     * Check that the exceptions thrown during building exits correctly
     */
    @Test
    fun testFatalExceptionConsumerBuild() {
        whenever(consumerBuilder.createPubSubConsumer(any(), any())).thenThrow(
            CordaMessageAPIFatalException(
                "Fatal Error",
                Exception()
            )
        )

        kafkaPubSubSubscription =
            KafkaPubSubSubscriptionImpl(subscriptionConfig, config, consumerBuilder, processor, executorService)

        kafkaPubSubSubscription.start()
        while (kafkaPubSubSubscription.isRunning) { }

        verify(mockCordaConsumer, times(0)).poll()
        verify(consumerBuilder, times(1)).createPubSubConsumer(any(), any())
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
        }.whenever(consumerBuilder).createPubSubConsumer(any(), any())
        whenever(mockCordaConsumer.poll()).thenThrow(CordaMessageAPIFatalException("Fatal Error", Exception()))

        kafkaPubSubSubscription =
            KafkaPubSubSubscriptionImpl(subscriptionConfig, config, consumerBuilder, processor, executorService)

        kafkaPubSubSubscription.start()
        while (kafkaPubSubSubscription.isRunning) {
        }

        assertThat(latch.count).isEqualTo(mockRecordCount)
        verify(consumerBuilder, times(2)).createPubSubConsumer(any(), any())
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount)).resetToLastCommittedPositions(any())
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount + 1)).poll()
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
        }.whenever(consumerBuilder).createPubSubConsumer(any(), any())

        latch = CountDownLatch(consumerPollAndProcessRetriesCount)
        processor = StubPubSubProcessor(latch, CordaMessageAPIFatalException("", Exception()))
        doReturn(mockConsumerRecords).whenever(mockCordaConsumer).poll()

        kafkaPubSubSubscription = KafkaPubSubSubscriptionImpl(
            subscriptionConfig, config, consumerBuilder,
            processor, executorService
        )

        kafkaPubSubSubscription.start()
        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        assertThat(latch.count).isEqualTo(0)
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount)).resetToLastCommittedPositions(any())
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount + 1)).poll()
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
        }.whenever(consumerBuilder).createPubSubConsumer(any(), any())

        latch = CountDownLatch(consumerPollAndProcessRetriesCount)
        processor = StubPubSubProcessor(latch, IOException())
        doReturn(mockConsumerRecords).whenever(mockCordaConsumer).poll()

        kafkaPubSubSubscription = KafkaPubSubSubscriptionImpl(
            subscriptionConfig, config, consumerBuilder,
            processor, executorService
        )

        kafkaPubSubSubscription.start()
        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        assertThat(latch.count).isEqualTo(0)
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount)).resetToLastCommittedPositions(any())
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount + 1)).poll()
    }
}
