package net.corda.libs.configuration.read.kafka.factory

import com.nhaarman.mockito_kotlin.mock
import net.corda.libs.configuration.read.ConfigRepository
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ConfigReadServiceFactoryImplTest {

    private val readServiceFactoryImpl = ConfigReadServiceFactoryImpl()
    private val configRepository: ConfigRepository = mock()

    @Test
    fun testCreateRepository() {
        Assertions.assertNotNull(readServiceFactoryImpl.createReadService(configRepository))
    }
}