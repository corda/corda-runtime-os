package net.corda.libs.configuration.write.kafka.factory

import com.typesafe.config.ConfigFactory
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock

class ConfigWriterFactoryImplTest {
    private lateinit var cordaWriteServiceFactory: ConfigWriterFactoryImpl
    private var publisherFactory: PublisherFactory = mock()
    private var publisher: Publisher = mock()
    private val topicName = "topic"

    @BeforeEach
    fun beforeEach() {
        cordaWriteServiceFactory = ConfigWriterFactoryImpl(publisherFactory)
    }

    @Test
    fun testCreateCordaWriteService() {
        Mockito.`when`(publisherFactory.createPublisher(any(), any()))
            .thenReturn(publisher)
        val writeService = cordaWriteServiceFactory.createWriter(topicName, ConfigFactory.empty())
        Assertions.assertNotNull(writeService)
    }
}