package net.corda.messaging.kafka.subscription.subscription.pubsub

import com.nhaarman.mockito_kotlin.*
import com.typesafe.config.Config
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.whenever
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_PROCESSOR_RETRIES
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.generateMockConsumerRecordList
import net.corda.messaging.kafka.subscription.subscriptions.pubsub.KafkaPubSubSubscription
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.mockito.Mockito
import java.lang.Exception

class KafkaPubSubSubscriptionTest {
    private companion object {
        private const val TOPIC = "topic1"
        private const val TEST_TIMEOUT = 30000L
    }

    private lateinit var kafkaPubSubSubscription: KafkaPubSubSubscription<String, ByteArray>
    private lateinit var subscriptionConfig: SubscriptionConfig
    private lateinit var config: Config
    private lateinit var consumerBuilder: ConsumerBuilder<String, ByteArray>
    private lateinit var processor: PubSubProcessor<String, ByteArray>
    private lateinit var mockCordaConsumer: CordaKafkaConsumer<String, ByteArray>
    private lateinit var mockConsumerRecords: List<ConsumerRecord<String, ByteArray>>

    private val consumerProcessorRetriesCount = 2
    private var executorService: ExecutorService? = null
    private var pollInvocationCount : Int = 0
    private var builderInvocationCount : Int = 0
    private var mockRecordCount = 5L
    private var maxTime = 0L

    @BeforeEach
    fun setup() {
        config = ConfigFactory.empty()
            .withValue(CONSUMER_PROCESSOR_RETRIES, ConfigValueFactory.fromAnyRef(consumerProcessorRetriesCount))
            .withValue(CONSUMER_THREAD_STOP_TIMEOUT, ConfigValueFactory.fromAnyRef(1000))
        subscriptionConfig = SubscriptionConfig("group1",  TOPIC, 1)
        consumerBuilder = mock()
        mockCordaConsumer = mock()
        processor = mock()

        mockConsumerRecords = generateMockConsumerRecordList(mockRecordCount, "topic", 1)
        val record = Record("topic", "key", "value".toByteArray())
        doReturn(mockCordaConsumer).whenever(consumerBuilder).createConsumer(subscriptionConfig)
        doReturn(record).whenever(mockCordaConsumer).getRecord(any())

        pollInvocationCount = 0
        builderInvocationCount = 0
        maxTime = System.currentTimeMillis() + TEST_TIMEOUT

        doAnswer{
            if (pollInvocationCount == 0) {
                pollInvocationCount++
                mockConsumerRecords
            } else {
                mutableListOf()
            }
        }.whenever(mockCordaConsumer).poll()
    }

    /**
     * Test processor is executed when a new record is added to the consumer.
     */
    @Test
    fun testPubSubConsumer() {
        kafkaPubSubSubscription = KafkaPubSubSubscription(subscriptionConfig, config, consumerBuilder, processor, executorService)
        kafkaPubSubSubscription.start()

        // the mocks will only ever return records
        while (Mockito.mockingDetails(processor).invocations.size < mockRecordCount) {
            if (System.currentTimeMillis() > maxTime) {
                fail("Timeout exceeded for test.")
            }
        }
        kafkaPubSubSubscription.stop()

        verify(processor, times(mockRecordCount.toInt())).onNext(any())
    }

    /**
     * Test processor is executed when the executor is used.
     */
    @Test
    fun testPubSubConsumerWithExecutor() {
        executorService = Executors.newFixedThreadPool(1)
        kafkaPubSubSubscription = KafkaPubSubSubscription(subscriptionConfig, config, consumerBuilder, processor, executorService)

        kafkaPubSubSubscription.start()

        while (Mockito.mockingDetails(processor).invocations.size < mockRecordCount) {
            if (System.currentTimeMillis() > maxTime) {
                fail("Timeout exceeded for test.")
            }
        }
        kafkaPubSubSubscription.stop()

        verify(processor, times(mockRecordCount.toInt())).onNext(any())
    }

    /**
     * Check that the exceptions thrown during building exits correctly
     */
    @Test
    fun testFatalExceptionConsumerBuild() {
        doAnswer {
            if (builderInvocationCount == 0) {
                builderInvocationCount++
                CordaMessageAPIFatalException("", Exception())
            } else {
                mockCordaConsumer
            }
        }.whenever(consumerBuilder).createConsumer(any())

        kafkaPubSubSubscription = KafkaPubSubSubscription(subscriptionConfig, config, consumerBuilder, processor, executorService)

        kafkaPubSubSubscription.start()
        while (Mockito.mockingDetails(consumerBuilder).invocations.size < 2 ||
            Mockito.mockingDetails(processor).invocations.size < mockRecordCount) {
            if (System.currentTimeMillis() > maxTime) {
                fail("Timeout exceeded for test.")
            }
        }
        kafkaPubSubSubscription.stop()

        verify(consumerBuilder, times(2)).createConsumer(any())
        verify(processor, times(5)).onNext(any())
    }


    /**
     * Check that the exceptions thrown during polling exits correctly
     */
    @Test
    fun testConsumerPollFailRetries() {
        doThrow(CordaMessageAPIFatalException("", Exception())).whenever(mockCordaConsumer).poll()

        kafkaPubSubSubscription = KafkaPubSubSubscription(subscriptionConfig, config, consumerBuilder, processor, executorService)

        kafkaPubSubSubscription.start()
        while (Mockito.mockingDetails(consumerBuilder).invocations.size < 2) {
            if (System.currentTimeMillis() > maxTime) {
                fail("Timeout exceeded for test.")
            }
        }

        kafkaPubSubSubscription.stop()

        verify(mockCordaConsumer, times(2)).resetToLastCommittedPositions(any())
    }


    /**
     * Check that the exceptions thrown during processing exits correctly
     */
    @Test
    fun testKafkaExceptionDuringProcessing() {
        doReturn(mockConsumerRecords).whenever(mockCordaConsumer).poll()
        doThrow(CordaMessageAPIFatalException("", Exception())).whenever(processor).onNext(any())
        kafkaPubSubSubscription = KafkaPubSubSubscription(subscriptionConfig, config, consumerBuilder, processor, executorService)

        kafkaPubSubSubscription.start()
        while (Mockito.mockingDetails(consumerBuilder).invocations.size < 2) {
            if (System.currentTimeMillis() > maxTime) {
                fail("Timeout exceeded for test.")
            }
        }

        kafkaPubSubSubscription.stop()

        verify(processor, times(consumerProcessorRetriesCount+1)).onNext(any())
        verify(mockCordaConsumer, times(consumerProcessorRetriesCount)).resetToLastCommittedPositions(any())
    }
}