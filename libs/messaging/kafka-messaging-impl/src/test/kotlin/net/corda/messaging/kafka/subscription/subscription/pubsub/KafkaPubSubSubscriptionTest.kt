package net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.subscription.subscription.pubsub

import com.nhaarman.mockito_kotlin.*
import com.typesafe.config.Config
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.whenever
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_PROCESSOR_RETRIES
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.generateMockConsumerRecords
import net.corda.messaging.kafka.subscription.subscriptions.pubsub.KafkaPubSubSubscription
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.KafkaException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class KafkaPubSubSubscriptionTest {
    private companion object {
        private const val TOPIC = "topic1"
    }

    private lateinit var kafkaPubSubSubscription: KafkaPubSubSubscription<String, ByteArray>
    private lateinit var subscriptionConfig: SubscriptionConfig
    private lateinit var config: Config
    private lateinit var consumerBuilder: ConsumerBuilder<String, ByteArray>
    private lateinit var processor: PubSubProcessor<String, ByteArray>
    private lateinit var mockCordaConsumer: CordaKafkaConsumer<String, ByteArray>
    private lateinit var mockConsumerRecords: List<ConsumerRecord<String, ByteArray>>
    private val consumerProcessorRetriesCount = 0
    private var executorService: ExecutorService? = null
    private var pollInvocationCount = 0

    @BeforeEach
    fun setup() {
        config = ConfigFactory.empty()
            .withValue(CONSUMER_PROCESSOR_RETRIES, ConfigValueFactory.fromAnyRef(consumerProcessorRetriesCount))
            .withValue(CONSUMER_THREAD_STOP_TIMEOUT, ConfigValueFactory.fromAnyRef(1000))
        subscriptionConfig = SubscriptionConfig("group1",  TOPIC, 1)
        consumerBuilder = mock()
        mockCordaConsumer = mock()
        processor = mock()

        mockConsumerRecords = generateMockConsumerRecords(5, "topic", 1)
        val record = Record("topic", "key", "value".toByteArray())
        doReturn(mockCordaConsumer).whenever(consumerBuilder).createConsumerAndSubscribe(subscriptionConfig)
        doReturn(record).whenever(mockCordaConsumer).getRecord(any())

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
        Thread.sleep(500)
        kafkaPubSubSubscription.stop()

        verify(processor, times(5)).onNext(any())
    }

    /**
     * Test processor is executed when the executor is used.
     */
    @Test
    fun testPubSubConsumerWithExecutor() {
        executorService = Executors.newFixedThreadPool(1)
        kafkaPubSubSubscription = KafkaPubSubSubscription(subscriptionConfig, config, consumerBuilder, processor, executorService)

        kafkaPubSubSubscription.start()

        Thread.sleep(500)
        kafkaPubSubSubscription.stop()

        verify(processor, times(5)).onNext(any())
    }

    /**
     * Check that the exceptions thrown during building exits correctly
     */
    @Test
    fun testKafkaExceptionConsumerBuild() {
        doThrow(KafkaException()).whenever(consumerBuilder).createConsumerAndSubscribe(any())

        kafkaPubSubSubscription = KafkaPubSubSubscription(subscriptionConfig, config, consumerBuilder, processor, executorService)

        kafkaPubSubSubscription.start()
        Thread.sleep(500)
        kafkaPubSubSubscription.stop()

        verify(processor, times(0)).onNext(any())
    }


    /**
     * Check that the exceptions thrown during processing exits correctly
     */
    @Test
    fun testKafkaExceptionDuringProcessing() {
        doThrow(KafkaException()).whenever(processor).onNext(any())
        kafkaPubSubSubscription = KafkaPubSubSubscription(subscriptionConfig, config, consumerBuilder, processor, executorService)

        kafkaPubSubSubscription.start()
        Thread.sleep(500)
        kafkaPubSubSubscription.stop()

        verify(processor, times(1)).onNext(any())
    }
}