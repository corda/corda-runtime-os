package net.corda.libs.configuration.read.file.factory

import com.typesafe.config.Config
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class FileConfigReaderFactoryImplTest {

    private val config: Config = mock()
    private val readServiceFactoryImpl = FileConfigReaderFactoryImpl()

    @Test
    fun testCreateRepository() {
        assertNotNull(readServiceFactoryImpl.createReader(config))
    }
}
