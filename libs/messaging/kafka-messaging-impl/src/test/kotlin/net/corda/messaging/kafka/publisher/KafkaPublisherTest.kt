package net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.publisher

import com.nhaarman.mockito_kotlin.*
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.KAFKA_TOPIC_PREFIX
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_CLOSE_TIMEOUT
import net.corda.messaging.kafka.publisher.KafkaPublisher
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.errors.InvalidProducerEpochException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.Duration

class KafkaPublisherTest {
    private lateinit var publisherConfig : PublisherConfig
    private lateinit var kafkaPublisher : KafkaPublisher<String, ByteArray>
    private lateinit var kafkaConfig: Config
    private val producer : MockProducer<String, ByteArray> = mock()

    @BeforeEach
    fun beforeEach() {
        publisherConfig  = PublisherConfig("clientId", "topic")
        kafkaConfig = ConfigFactory.empty().withValue(PRODUCER_CLOSE_TIMEOUT, ConfigValueFactory.fromAnyRef(1))
        kafkaConfig = kafkaConfig.withValue(KAFKA_TOPIC_PREFIX, ConfigValueFactory.fromAnyRef("prefix"))
    }

    @Test
    fun testPublish() {
        kafkaPublisher = KafkaPublisher(publisherConfig, kafkaConfig, producer)

        val record = Record("topic", "key1", "value1".toByteArray())
        kafkaPublisher.publish(record)
        verify(producer, times(1)).send(any(), any())
        verify(producer, times(0)).beginTransaction()
        verify(producer, times(0)).commitTransaction()

    }

    @Test
    fun testTransactionPublish() {
        publisherConfig  = PublisherConfig("clientId", "topic", 1)
        kafkaPublisher = KafkaPublisher(publisherConfig, kafkaConfig, producer)

        val record = Record("topic", "key1", "value1".toByteArray())
        kafkaPublisher.publish(record)
        verify(producer, times(1)).send(any(), any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testSafeClose() {
        kafkaPublisher = KafkaPublisher(publisherConfig, kafkaConfig, producer)

        kafkaPublisher.safeClose()
        verify(producer, times(1)).close(Mockito.any(Duration::class.java))
    }

    @Test
    fun testRetryTransactions() {
        doThrow(InvalidProducerEpochException("")).whenever(producer).beginTransaction()

        publisherConfig  = PublisherConfig("clientId", "topic", 1)
        kafkaPublisher = KafkaPublisher(publisherConfig, kafkaConfig, producer)

        val record = Record("topic", "key1", "value1".toByteArray())
        kafkaPublisher.publish(record)
        verify(producer, times(0)).send(any(), any())
        verify(producer, times(0)).commitTransaction()
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).abortTransaction()
    }
}