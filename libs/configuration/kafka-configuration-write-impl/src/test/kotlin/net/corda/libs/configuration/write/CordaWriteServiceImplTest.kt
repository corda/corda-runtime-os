package net.corda.libs.configuration.write

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.typesafe.config.ConfigFactory
import net.corda.data.config.Configuration
import net.corda.messaging.api.publisher.Publisher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.BufferedReader

class CordaWriteServiceImplTest {
    private lateinit var cordaWriteService: CordaWriteService
    private var publisher: Publisher<String, Configuration> = mock()

    @BeforeEach
    fun beforeEach() {
        cordaWriteService = CordaWriteServiceImpl("topic", publisher)
    }

    @Test
    fun testUpdateConfiguration() {
        val configReader = BufferedReader(this::class.java.classLoader.getResourceAsStream("config.conf").reader())
        val config = ConfigFactory.parseString(configReader.readText())
        configReader.close()

        val packageVersion = CordaConfigurationVersion("corda", 1, 0)
        val componentVersion = CordaConfigurationVersion("corda", 1, 0)
        val configurationKey = CordaConfigurationKey("corda", packageVersion, componentVersion)

        cordaWriteService.updateConfiguration(configurationKey, config)
        verify(publisher, times(2)).publish(any())
    }
}