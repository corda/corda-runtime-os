package net.corda.libs.configuration.write.factory

import com.nhaarman.mockito_kotlin.mock
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CordaWriteServiceFactoryImplTest {
    private lateinit var cordaWriteServiceFactory: CordaWriteServiceFactoryImpl
    private val topicName = "topic"

    @BeforeEach
    fun beforeEach() {
        cordaWriteServiceFactory = CordaWriteServiceFactoryImpl(mock())
    }

    @Test
    fun testCreateCordaWriteService() {
        val writeService = cordaWriteServiceFactory.getWriteService(topicName)
        Assertions.assertNotNull(writeService)
    }
}