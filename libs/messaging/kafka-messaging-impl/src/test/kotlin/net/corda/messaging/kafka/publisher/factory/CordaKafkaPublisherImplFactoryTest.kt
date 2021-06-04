package net.corda.messaging.kafka.publisher.factory

import com.nhaarman.mockito_kotlin.mock
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.KAFKA_TOPIC_PREFIX
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_CLOSE_TIMEOUT
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CordaKafkaPublisherImplFactoryTest {
    private lateinit var cordaKafkaPublisherFactory : CordaKafkaPublisherFactory
    private lateinit var kafkaConfig: Config
    private val publisherConfig = PublisherConfig("clientId")

    @BeforeEach
    fun beforeEach() {
        cordaKafkaPublisherFactory = CordaKafkaPublisherFactory(mock())
        kafkaConfig = ConfigFactory.empty().withValue(PRODUCER_CLOSE_TIMEOUT, ConfigValueFactory.fromAnyRef(1))
            .withValue(KAFKA_TOPIC_PREFIX, ConfigValueFactory.fromAnyRef("prefix"))
    }

    @Test
    fun testCreatePublisher() {
        val publisher = cordaKafkaPublisherFactory.createPublisher(publisherConfig, mapOf())
        assertNotNull(publisher)
    }
}
