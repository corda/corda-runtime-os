package net.corda.libs.configuration.read.file.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class FileConfigReaderFactoryImplTest {

    private val configFactory = mock<SmartConfigFactory>()
    private val config = mock<SmartConfig>() {
        on { factory } doReturn configFactory
    }
    private val readServiceFactoryImpl = FileConfigReaderFactoryImpl()

    @Test
    fun testCreateRepository() {
        assertNotNull(readServiceFactoryImpl.createReader(config))
    }
}
