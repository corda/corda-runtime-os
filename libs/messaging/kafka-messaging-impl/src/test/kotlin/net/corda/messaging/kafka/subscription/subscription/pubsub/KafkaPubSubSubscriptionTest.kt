package net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.subscription.subscription.pubsub

import com.nhaarman.mockito_kotlin.*
import com.typesafe.config.Config
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_POLL_TIMEOUT
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_CREATE_MAX_RETRIES
import net.corda.messaging.kafka.subscription.createMockConsumerAndAddRecords
import net.corda.messaging.kafka.subscription.consumer.ConsumerBuilder
import net.corda.messaging.kafka.subscription.subscriptions.pubsub.KafkaPubSubSubscription
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class KafkaPubSubSubscriptionTest {
    private companion object {
        private const val NUMBER_OF_RECORDS = 5L
        private const val TOPIC = "topic1"
    }

    private lateinit var kafkaPubSubSubscription: KafkaPubSubSubscription<String, ByteArray>
    private lateinit var subscriptionConfig: SubscriptionConfig
    private lateinit var config: Config
    private lateinit var properties: Properties
    private lateinit var consumerBuilder: ConsumerBuilder<String, ByteArray>
    private lateinit var processor: PubSubProcessor<String, ByteArray>
    private lateinit var mockConsumer: MockConsumer<String, ByteArray>
    private lateinit var mockPartition: TopicPartition
    private var executorService: ExecutorService? = null


    @BeforeEach
    fun setup() {
        config = mock()
        subscriptionConfig = SubscriptionConfig("group1",  TOPIC, 1)
        properties = Properties()
        properties[CONSUMER_CREATE_MAX_RETRIES] = 10
        properties[CONSUMER_POLL_TIMEOUT] = 10L
        properties[CONSUMER_THREAD_STOP_TIMEOUT] = 10L
        consumerBuilder = mock()
        processor = mock()
        val (consumer, partition) = createMockConsumerAndAddRecords(TOPIC, NUMBER_OF_RECORDS, OffsetResetStrategy.LATEST)
        mockConsumer = consumer
        mockPartition = partition
        doReturn(mockConsumer).whenever(consumerBuilder).createConsumer(properties)

    }

    /**
     * Test processor is executed when a new record is added to the consumer.
     */
    @Test
    fun testPubSubConsumer() {
        kafkaPubSubSubscription = KafkaPubSubSubscription(subscriptionConfig, properties, consumerBuilder, processor, executorService)

        kafkaPubSubSubscription.start()

        val position = mockConsumer.position(mockPartition)
        mockConsumer.addRecord(ConsumerRecord(
            TOPIC, mockPartition.partition(),
            position, "key$position", "value$position".toByteArray()))
        Thread.sleep(500)
        kafkaPubSubSubscription.stop()


        verify(processor, times(1)).onNext(any())
        Assertions.assertThat(mockConsumer.position(mockPartition)).isEqualTo(NUMBER_OF_RECORDS +1)
    }

    /**
     * Test processor is executed when the executor is used.
     */
    @Test
    fun testPubSubConsumerWithExecutor() {
        executorService = Executors.newFixedThreadPool(1)
        kafkaPubSubSubscription = KafkaPubSubSubscription(subscriptionConfig, properties, consumerBuilder, processor, executorService)

        kafkaPubSubSubscription.start()
        val position = mockConsumer.position(mockPartition)
        mockConsumer.addRecord(ConsumerRecord(
            TOPIC, mockPartition.partition(),
            position, "key$position", "value$position".toByteArray()))
        Thread.sleep(500)
        kafkaPubSubSubscription.stop()

        verify(processor, times(1)).onNext(any())
        Assertions.assertThat(mockConsumer.position(mockPartition)).isEqualTo(NUMBER_OF_RECORDS +1)
    }

    /**
     * Check that the position on the topic has not been updated after a KafkaException is thrown during processing
     */
    @Test
    fun testKafkaExceptionResetOffset() {
        doThrow(KafkaException()).whenever(consumerBuilder).createConsumer(any())

        kafkaPubSubSubscription = KafkaPubSubSubscription(subscriptionConfig, properties, consumerBuilder, processor, executorService)

        kafkaPubSubSubscription.start()
        Thread.sleep(500)
        kafkaPubSubSubscription.stop()

        verify(processor, times(0)).onNext(any())
        Assertions.assertThat(mockConsumer.position(mockPartition)).isEqualTo(NUMBER_OF_RECORDS)
    }
}