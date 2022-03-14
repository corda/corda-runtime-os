package net.corda.messaging.publisher.factory

import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.publisher.config.PublisherConfig
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class CordaPublisherImplFactoryTest {
    private lateinit var cordaPublisherFactory: CordaPublisherFactory
    private val publisherConfig = PublisherConfig("clientId")

    private val cordaProducerBuilder: CordaProducerBuilder = mock()
    private val cordaProducer: CordaProducer = mock()

    @BeforeEach
    fun beforeEach() {
        doReturn(cordaProducer).`when`(cordaProducerBuilder).createProducer(any(), any())
        cordaPublisherFactory = CordaPublisherFactory(mock(), cordaProducerBuilder, mock(), mock())
    }

    @Test
    fun testCreatePublisher() {
        val messagingConfig = SmartConfigImpl.empty()
            .withValue("messaging.topic.prefix", ConfigValueFactory.fromAnyRef("demo"))
        val publisher = cordaPublisherFactory.createPublisher(publisherConfig, messagingConfig)
        assertNotNull(publisher)
    }
}
