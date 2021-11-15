package net.corda.libs.configuration.read.file.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class FileConfigReaderFactoryImplTest {

    private val config: SmartConfig = mock()
    private val smartConfigFactory: SmartConfigFactory = mock()
    private val readServiceFactoryImpl = FileConfigReaderFactoryImpl(smartConfigFactory)

    @Test
    fun testCreateRepository() {
        assertNotNull(readServiceFactoryImpl.createReader(config))
    }
}
