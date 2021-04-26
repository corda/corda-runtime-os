package net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.publisher

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.KAFKA_TOPIC_PREFIX
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_CLOSE_TIMEOUT
import net.corda.messaging.kafka.publisher.KafkaPublisher
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat

class KafkaPublisherTest {
    private lateinit var publisherConfig : PublisherConfig
    private lateinit var kafkaPublisher : KafkaPublisher<String, ByteArray>
    private lateinit var producer : MockProducer<String, ByteArray>
    private lateinit var kafkaConfig: Config

    @BeforeEach
    fun beforeEach() {
        publisherConfig  = PublisherConfig("clientId", "topic")
        kafkaConfig = ConfigFactory.empty().withValue(PRODUCER_CLOSE_TIMEOUT, ConfigValueFactory.fromAnyRef(1))
        kafkaConfig = kafkaConfig.withValue(KAFKA_TOPIC_PREFIX, ConfigValueFactory.fromAnyRef("prefix"))
        producer = MockProducer(true, null, StringSerializer(), ByteArraySerializer())
    }

    @Test
    fun testPublish() {
        kafkaPublisher = KafkaPublisher(publisherConfig, kafkaConfig, producer)

        val record = Record("topic", "key1", "value1".toByteArray())
        val fut = kafkaPublisher.publish(record)
        assertThat(fut.get())
    }

    @Test
    fun testTransactionPublish() {
        producer.initTransactions()
        publisherConfig  = PublisherConfig("clientId", "topic", 1)

        kafkaPublisher = KafkaPublisher(publisherConfig, kafkaConfig, producer)

        val record = Record("topic", "key1", "value1".toByteArray())
        val fut = kafkaPublisher.publish(record)
        assertThat(fut.get())
    }
}