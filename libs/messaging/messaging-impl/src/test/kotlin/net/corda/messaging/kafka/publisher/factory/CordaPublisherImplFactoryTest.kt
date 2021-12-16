package net.corda.messaging.kafka.publisher.factory

import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.publisher.config.PublisherConfig
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class CordaPublisherImplFactoryTest {
    private lateinit var cordaKafkaPublisherFactory: CordaKafkaPublisherFactory
    private val publisherConfig = PublisherConfig("clientId")

    @BeforeEach
    fun beforeEach() {
        cordaKafkaPublisherFactory = CordaKafkaPublisherFactory(mock(), mock(), mock(), mock())
    }

    @Test
    fun testCreatePublisher() {
        val nodeConfig = SmartConfigImpl.empty()
            .withValue("messaging.topic.prefix", ConfigValueFactory.fromAnyRef("demo"))
        val publisher = cordaKafkaPublisherFactory.createPublisher(publisherConfig, nodeConfig)
        assertNotNull(publisher)
    }
}
