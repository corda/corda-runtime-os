package net.corda.libs.configuration.write.kafka.factory

import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock

class ConfigWriterFactoryImplTest {
    @Suppress("Deprecation")
    private lateinit var cordaWriteServiceFactory: ConfigWriterFactoryImpl
    private var publisherFactory: PublisherFactory = mock()
    private var publisher: Publisher = mock()
    private val topicName = "topic"

    @Suppress("Deprecation")
    @BeforeEach
    fun beforeEach() {
        cordaWriteServiceFactory = ConfigWriterFactoryImpl(publisherFactory)
    }

    @Test
    fun testCreateCordaWriteService() {
        Mockito.`when`(publisherFactory.createPublisher(any(), any()))
            .thenReturn(publisher)
        val writeService = cordaWriteServiceFactory.createWriter(topicName, SmartConfigImpl.empty())
        Assertions.assertNotNull(writeService)
    }
}