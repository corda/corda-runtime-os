package net.corda.libs.configuration.write.kafka

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.write.ConfigWriteService
import net.corda.libs.configuration.write.CordaConfigurationKey
import net.corda.libs.configuration.write.CordaConfigurationVersion
import net.corda.messaging.api.publisher.Publisher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.BufferedReader

class ConfigWriteServiceImplTest {
    private lateinit var configWriteService: ConfigWriteService
    private var publisher: Publisher = mock()

    @BeforeEach
    fun beforeEach() {
        configWriteService = ConfigWriteServiceImpl("topic", publisher)
    }

    @Test
    fun testUpdateConfiguration() {
        val configReader = BufferedReader(this::class.java.classLoader.getResourceAsStream("config.conf").reader())
        val config = ConfigFactory.parseString(configReader.readText())
        configReader.close()

        val packageVersion = CordaConfigurationVersion("corda", 1, 0)
        val componentVersion = CordaConfigurationVersion("corda", 1, 0)
        val configurationKey = CordaConfigurationKey("corda", packageVersion, componentVersion)

        configWriteService.updateConfiguration(configurationKey, config)
        verify(publisher, times(1)).publish(any())
    }
}