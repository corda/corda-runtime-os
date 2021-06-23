package net.corda.messaging.kafka.publisher.factory

import com.nhaarman.mockito_kotlin.mock
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.publisher.config.PublisherConfig
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CordaKafkaPublisherImplFactoryTest {
    private lateinit var cordaKafkaPublisherFactory: CordaKafkaPublisherFactory
    private val publisherConfig = PublisherConfig("clientId")

    @BeforeEach
    fun beforeEach() {
        cordaKafkaPublisherFactory = CordaKafkaPublisherFactory(mock())
    }

    @Test
    fun testCreatePublisher() {
        val nodeConfig = ConfigFactory.empty()
            .withValue("messaging.topic.prefix", ConfigValueFactory.fromAnyRef("demo"))
        val publisher = cordaKafkaPublisherFactory.createPublisher(publisherConfig, nodeConfig)
        assertNotNull(publisher)
    }
}
