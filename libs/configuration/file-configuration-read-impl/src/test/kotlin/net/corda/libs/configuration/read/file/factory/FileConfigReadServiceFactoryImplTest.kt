package net.corda.libs.configuration.read.file.factory

import com.typesafe.config.Config
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class FileConfigReadServiceFactoryImplTest {

    private val config: Config = mock()
    private val readServiceFactoryImpl = FileConfigReadServiceFactoryImpl()

    @Test
    fun testCreateRepository() {
        assertNotNull(readServiceFactoryImpl.createReadService(config))
    }
}
