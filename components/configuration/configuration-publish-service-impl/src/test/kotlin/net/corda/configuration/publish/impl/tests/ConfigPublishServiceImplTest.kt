package net.corda.configuration.publish.impl.tests

import net.corda.configuration.publish.ConfigPublishService
import net.corda.configuration.publish.impl.ConfigPublishServiceImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.publish.ConfigPublisher
import net.corda.libs.configuration.publish.CordaConfigurationKey
import net.corda.libs.configuration.publish.CordaConfigurationVersion
import net.corda.libs.configuration.publish.factory.ConfigPublisherFactory
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.io.BufferedReader

class ConfigPublishServiceImplTest {
    private lateinit var configPublishService: ConfigPublishService
    private val configPublisherFactory: ConfigPublisherFactory = mock()
    private val configPublisher: ConfigPublisher = mock()
    private val config: SmartConfig = mock()

    @Captor
    var keyCaptor: ArgumentCaptor<CordaConfigurationKey> = ArgumentCaptor.forClass(CordaConfigurationKey::class.java)

    @BeforeEach
    fun beforeEach() {
        configPublishService = ConfigPublishServiceImpl(configPublisherFactory)
        Mockito.`when`(configPublisherFactory.createPublisher(any(), any()))
            .thenReturn(configPublisher)
    }

    @Test
    fun `test config is read and saved properly`() {
        val configString = readConfigFile("config.conf")

        configPublishService.updateConfig("dummyTopic", config, configString)

        val cordaDatabaseKey = CordaConfigurationKey(
            "corda",
            CordaConfigurationVersion("corda", 5, 4),
            CordaConfigurationVersion("database", 5, 2)
        )

        val cordaSecurityKey = CordaConfigurationKey(
            "corda",
            CordaConfigurationVersion("corda", 5, 4),
            CordaConfigurationVersion("security", 5, 3)
        )

        verify(configPublisher, times(2)).updateConfiguration(capture(keyCaptor), any())
        Assertions.assertEquals(cordaDatabaseKey, keyCaptor.allValues[0])
        Assertions.assertEquals(cordaSecurityKey, keyCaptor.allValues[1])
    }

    @Test
    fun `test nested config does not cause issues `() {
        val configString = readConfigFile("nestedConfig.conf")
        configPublishService.updateConfig("dummyTopic", config, configString)

        val cordaDatabaseKey = CordaConfigurationKey(
            "corda",
            CordaConfigurationVersion("corda", 5, 4),
            CordaConfigurationVersion("database", 5, 2)
        )

        val cordaSecurityKey = CordaConfigurationKey(
            "corda",
            CordaConfigurationVersion("corda", 5, 4),
            CordaConfigurationVersion("security", 5, 3)
        )

        verify(configPublisher, times(2)).updateConfiguration(capture(keyCaptor), any())
        Assertions.assertEquals(cordaDatabaseKey, keyCaptor.allValues[0])
        Assertions.assertEquals(cordaSecurityKey, keyCaptor.allValues[1])
    }

    @Test
    fun `test saving multi package configs while filtering out unversioned packages `() {
        val configString = readConfigFile("multipleConfig.conf")
        configPublishService.updateConfig("dummyTopic", config, configString)

        val cordaDatabaseKey = CordaConfigurationKey(
            "corda",
            CordaConfigurationVersion("corda", 5, 4),
            CordaConfigurationVersion("database", 5, 2)
        )

        val cordaSecurityKey = CordaConfigurationKey(
            "corda",
            CordaConfigurationVersion("corda", 5, 4),
            CordaConfigurationVersion("security", 5, 3)
        )

        val improvedCordaDatabaseKey = CordaConfigurationKey(
            "improvedCorda",
            CordaConfigurationVersion("improvedCorda", 5, 4),
            CordaConfigurationVersion("database", 5, 2)
        )

        val improvedCordaSecurityKey = CordaConfigurationKey(
            "improvedCorda",
            CordaConfigurationVersion("improvedCorda", 5, 4),
            CordaConfigurationVersion("security", 5, 3)
        )

        verify(configPublisher, times(4)).updateConfiguration(capture(keyCaptor), any())
        Assertions.assertEquals(cordaDatabaseKey, keyCaptor.allValues[0])
        Assertions.assertEquals(cordaSecurityKey, keyCaptor.allValues[1])
        Assertions.assertEquals(improvedCordaDatabaseKey, keyCaptor.allValues[2])
        Assertions.assertEquals(improvedCordaSecurityKey, keyCaptor.allValues[3])
    }

    @Test
    fun `test unversioned config and rogue properties are filtered out and correct config is saved`() {
        val configString = readConfigFile("badConfig.conf")
        configPublishService.updateConfig("dummyTopic", config, configString)

        val cordaDatabaseKey = CordaConfigurationKey(
            "corda",
            CordaConfigurationVersion("corda", 5, 4),
            CordaConfigurationVersion("database", 5, 2)
        )

        val cordaSecurityKey = CordaConfigurationKey(
            "corda",
            CordaConfigurationVersion("corda", 5, 4),
            CordaConfigurationVersion("security", 5, 3)
        )

        verify(configPublisher, times(2)).updateConfiguration(capture(keyCaptor), any())
        Assertions.assertEquals(cordaDatabaseKey, keyCaptor.allValues[0])
        Assertions.assertEquals(cordaSecurityKey, keyCaptor.allValues[1])
    }

    private fun readConfigFile(file: String): String {
        val configReader =
            BufferedReader(this::class.java.classLoader.getResourceAsStream(file).reader())
        val configString = configReader.readText()
        configReader.close()
        return configString
    }
}
