package net.corda.comp.kafka.config.write

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.capture
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.typesafe.config.Config
import net.corda.libs.configuration.write.ConfigWriteService
import net.corda.libs.configuration.write.CordaConfigurationKey
import net.corda.libs.configuration.write.CordaConfigurationVersion
import net.corda.libs.configuration.write.factory.ConfigWriteServiceFactory
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito
import java.io.BufferedReader

class KafkaConfigWriteTest {

    private lateinit var kafkaConfigWrite: KafkaConfigWrite
    private val configWriteServiceFactory: ConfigWriteServiceFactory = mock()
    private val configWriteService: ConfigWriteService = mock()
    private val config: Config = mock()

    @Captor
    var keyCaptor: ArgumentCaptor<CordaConfigurationKey> = ArgumentCaptor.forClass(CordaConfigurationKey::class.java)


    @BeforeEach
    fun beforeEach() {
        kafkaConfigWrite = KafkaConfigWrite(configWriteServiceFactory)
        Mockito.`when`(configWriteServiceFactory.createWriteService(any(), any()))
            .thenReturn(configWriteService)
    }


    @Test
    fun `test config is read and saved properly`() {
        val configString = readConfigFile("config.conf")

        kafkaConfigWrite.updateConfig("dummyTopic", config, configString)

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

        verify(configWriteService, times(2)).updateConfiguration(capture(keyCaptor), any())
        Assertions.assertEquals(cordaDatabaseKey, keyCaptor.allValues[0])
        Assertions.assertEquals(cordaSecurityKey, keyCaptor.allValues[1])

    }

    @Test
    fun `test nested config does not cause issues `() {
        val configString = readConfigFile("nestedConfig.conf")
        kafkaConfigWrite.updateConfig("dummyTopic", config, configString)

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

        verify(configWriteService, times(2)).updateConfiguration(capture(keyCaptor), any())
        Assertions.assertEquals(cordaDatabaseKey, keyCaptor.allValues[0])
        Assertions.assertEquals(cordaSecurityKey, keyCaptor.allValues[1])
    }



    @Test
    fun `test saving multi package configs while filtering out unversioned packages `() {
        val configString = readConfigFile("multipleConfig.conf")
        kafkaConfigWrite.updateConfig("dummyTopic", config, configString)

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

        verify(configWriteService, times(4)).updateConfiguration(capture(keyCaptor), any())
        Assertions.assertEquals(cordaDatabaseKey, keyCaptor.allValues[0])
        Assertions.assertEquals(cordaSecurityKey, keyCaptor.allValues[1])
        Assertions.assertEquals(improvedCordaDatabaseKey, keyCaptor.allValues[2])
        Assertions.assertEquals(improvedCordaSecurityKey, keyCaptor.allValues[3])
    }

    @Test
    fun `test unversioned config and rogue properties are filtered out and correct config is saved`() {
        val configString = readConfigFile("badConfig.conf")
        kafkaConfigWrite.updateConfig("dummyTopic", config, configString)

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

        verify(configWriteService, times(2)).updateConfiguration(capture(keyCaptor), any())
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