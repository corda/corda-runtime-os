package net.corda.libs.configuration.write.factory

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import net.corda.data.config.Configuration
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class CordaWriteServiceFactoryImplTest {
    private lateinit var cordaWriteServiceFactory: CordaWriteServiceFactoryImpl
    private var publisherFactory: PublisherFactory = mock()
    private var publisher: Publisher<String, Configuration> = mock()
    private val topicName = "topic"

    @BeforeEach
    fun beforeEach() {
        cordaWriteServiceFactory = CordaWriteServiceFactoryImpl(publisherFactory)
    }

    @Test
    fun testCreateCordaWriteService() {
        Mockito.`when`(publisherFactory.createPublisher<String,Configuration>(any(), any(), any(), any()))
            .thenReturn(publisher)
        val writeService = cordaWriteServiceFactory.createWriteService(topicName)
        Assertions.assertNotNull(writeService)
    }
}