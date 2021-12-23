package net.corda.libs.configuration.publish.impl.factory

import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock

class ConfigPublishFactoryImplTest {
    private lateinit var cordaPublisherFactory: ConfigPublisherFactoryImpl
    private var publisherFactory: PublisherFactory = mock()
    private var publisher: Publisher = mock()
    private val topicName = "topic"

    @BeforeEach
    fun beforeEach() {
        cordaPublisherFactory = ConfigPublisherFactoryImpl(publisherFactory)
    }

    @Test
    fun testCreateCordaWriteService() {
        Mockito.`when`(publisherFactory.createPublisher(any(), any()))
            .thenReturn(publisher)
        val writeService = cordaPublisherFactory.createPublisher(topicName, SmartConfigImpl.empty())
        Assertions.assertNotNull(writeService)
    }
}