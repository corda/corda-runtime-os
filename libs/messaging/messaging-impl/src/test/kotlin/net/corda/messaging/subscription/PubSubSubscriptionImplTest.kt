package net.corda.messaging.subscription

import com.typesafe.config.Config
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.CONSUMER_POLL_AND_PROCESS_RETRIES
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.builder.MessageBusConsumerBuilder
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.createStandardTestConfig
import net.corda.messaging.generateMockCordaConsumerRecordList
import net.corda.messaging.properties.ConfigProperties.Companion.PATTERN_PUBSUB
import net.corda.messaging.stubs.StubPubSubProcessor
import net.corda.messaging.subscription.consumer.builder.CordaConsumerBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PubSubSubscriptionImplTest {
    private companion object {
        private const val TEST_TIMEOUT_SECONDS = 30L
    }

    private var mockRecordCount = 5L
    private val config: Config = createStandardTestConfig().getConfig(PATTERN_PUBSUB)
    private val consumerPollAndProcessRetriesCount = config.getInt(CONSUMER_POLL_AND_PROCESS_RETRIES)
    private val cordaConsumerBuilder: MessageBusConsumerBuilder = mock()
    private val mockCordaConsumer: CordaConsumer<String, ByteBuffer> = mock()
    private val mockConsumerRecords = generateMockCordaConsumerRecordList(mockRecordCount, "topic", 1)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock()
    private val lifecycleCoordinator: LifecycleCoordinator = mock()

    private var executorService: ExecutorService? = null
    private var pollInvocationCount: Int = 0
    private var builderInvocationCount: Int = 0
    private lateinit var kafkaPubSubSubscription: PubSubSubscriptionImpl<String, ByteBuffer>
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
        doReturn(mockCordaConsumer).whenever(cordaConsumerBuilder).createPubSubConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any()
        )
        doReturn(lifecycleCoordinator).`when`(lifecycleCoordinatorFactory).createCoordinator(any(), any())
    }

    /**
     * Test processor is executed when a new record is added to the consumer.
     */
    @Test
    fun testPubSubConsumer() {
        kafkaPubSubSubscription =
            PubSubSubscriptionImpl(
                config,
                cordaConsumerBuilder,
                processor,
                executorService,
                lifecycleCoordinatorFactory
            )
        kafkaPubSubSubscription.start()

        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        kafkaPubSubSubscription.stop()
        assertThat(latch.count).isEqualTo(0)
        verify(cordaConsumerBuilder, times(1)).createConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any(),
            any()
        )
    }

    /**
     * Test processor is executed when the executor is used.
     */
    @Test
    fun testPubSubConsumerWithExecutor() {
        executorService = Executors.newFixedThreadPool(1)
        kafkaPubSubSubscription =
            PubSubSubscriptionImpl(
                config,
                cordaConsumerBuilder,
                processor,
                executorService,
                lifecycleCoordinatorFactory
            )

        kafkaPubSubSubscription.start()

        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        kafkaPubSubSubscription.stop()
        assertThat(latch.count).isEqualTo(0)
        verify(cordaConsumerBuilder, times(1)).createConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any(),
            any()
        )
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
        }.whenever(cordaConsumerBuilder).createConsumer<String, ByteBuffer>(any(), any(), any(), any(), any())
        doReturn(mockConsumerRecords).whenever(mockCordaConsumer).poll()

        doThrow(CordaMessageAPIFatalException::class).whenever(mockCordaConsumer).commitSyncOffsets(any(), anyOrNull())
        kafkaPubSubSubscription =
            PubSubSubscriptionImpl(
                config,
                cordaConsumerBuilder,
                processor,
                executorService,
                lifecycleCoordinatorFactory
            )

        kafkaPubSubSubscription.start()
        @Suppress("EmptyWhileBlock")
        while (kafkaPubSubSubscription.isRunning) {
        }

        assertThat(latch.count).isEqualTo(1)
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount + 1)).poll()
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount + 1)).commitSyncOffsets(any(), isNull())
        verify(cordaConsumerBuilder, times(2)).createConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any(),
            any()
        )
    }

    /**
     * Check that the exceptions thrown during building exits correctly
     */
    @Test
    fun testFatalExceptionConsumerBuild() {
        whenever(cordaConsumerBuilder.createConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any(),
            any()
        )).thenThrow(
            CordaMessageAPIFatalException(
                "Fatal Error",
                Exception()
            )
        )

        kafkaPubSubSubscription =
            PubSubSubscriptionImpl(
                config,
                cordaConsumerBuilder,
                processor,
                executorService,
                lifecycleCoordinatorFactory
            )

        kafkaPubSubSubscription.start()
        @Suppress("EmptyWhileBlock")
        while (kafkaPubSubSubscription.isRunning) {
        }

        verify(mockCordaConsumer, times(0)).poll()
        verify(cordaConsumerBuilder, times(1)).createConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any(),
            any()
        )
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
        }.whenever(cordaConsumerBuilder).createConsumer<String, ByteBuffer>(any(), any(), any(), any(), any())
        whenever(mockCordaConsumer.poll()).thenThrow(CordaMessageAPIFatalException("Fatal Error", Exception()))

        kafkaPubSubSubscription =
            PubSubSubscriptionImpl(
                config,
                cordaConsumerBuilder,
                processor,
                executorService,
                lifecycleCoordinatorFactory
            )

        kafkaPubSubSubscription.start()
        @Suppress("EmptyWhileBlock")
        while (kafkaPubSubSubscription.isRunning) {
        }

        assertThat(latch.count).isEqualTo(mockRecordCount)
        verify(cordaConsumerBuilder, times(2)).createConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any(),
            any()
        )
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
        }.whenever(cordaConsumerBuilder).createConsumer<String, ByteBuffer>(any(), any(), any(), any(), any())

        latch = CountDownLatch(consumerPollAndProcessRetriesCount)
        processor = StubPubSubProcessor(latch, CordaMessageAPIFatalException("", Exception()))
        doReturn(mockConsumerRecords).whenever(mockCordaConsumer).poll()

        kafkaPubSubSubscription = PubSubSubscriptionImpl(
            config, cordaConsumerBuilder,
            processor, executorService, lifecycleCoordinatorFactory
        )

        kafkaPubSubSubscription.start()
        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        @Suppress("EmptyWhileBlock")
        while (kafkaPubSubSubscription.isRunning) {
        }
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
        }.whenever(cordaConsumerBuilder).createConsumer<String, ByteBuffer>(any(), any(), any(), any(), any())

        latch = CountDownLatch(consumerPollAndProcessRetriesCount)
        processor = StubPubSubProcessor(latch, IOException())
        doReturn(mockConsumerRecords).whenever(mockCordaConsumer).poll()

        kafkaPubSubSubscription = PubSubSubscriptionImpl(
            config, cordaConsumerBuilder,
            processor, executorService, lifecycleCoordinatorFactory
        )

        kafkaPubSubSubscription.start()
        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        @Suppress("EmptyWhileBlock")
        while (kafkaPubSubSubscription.isRunning) {}
        assertThat(latch.count).isEqualTo(0)
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount)).resetToLastCommittedPositions(any())
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount + 1)).poll()
    }
}
