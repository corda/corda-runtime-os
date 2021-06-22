package net.corda.messaging.kafka.publisher.factory

import com.nhaarman.mockito_kotlin.mock
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.createStandardTestConfig
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CordaKafkaPublisherImplFactoryTest {
    private lateinit var cordaKafkaPublisherFactory : CordaKafkaPublisherFactory
    private val publisherConfig = PublisherConfig("clientId")

    @BeforeEach
    fun beforeEach() {
        cordaKafkaPublisherFactory = CordaKafkaPublisherFactory(mock())
    }

    @Test
    fun testCreatePublisher() {
        val publisher = cordaKafkaPublisherFactory.createPublisher(publisherConfig, createStandardTestConfig())
        assertNotNull(publisher)
    }
}
