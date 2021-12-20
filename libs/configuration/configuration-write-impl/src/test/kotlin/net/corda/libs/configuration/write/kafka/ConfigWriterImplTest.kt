@file:Suppress("DEPRECATION")

package net.corda.libs.configuration.write.kafka

import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.write.ConfigWriter
import net.corda.libs.configuration.write.CordaConfigurationKey
import net.corda.libs.configuration.write.CordaConfigurationVersion
import net.corda.messaging.api.publisher.Publisher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.BufferedReader

class ConfigWriterImplTest {
    private lateinit var configWriter: ConfigWriter
    private var publisher: Publisher = mock()

    @BeforeEach
    fun beforeEach() {
        configWriter = ConfigWriterImpl("topic", publisher)
    }

    @Test
    fun testUpdateConfiguration() {
        val configReader = BufferedReader(this::class.java.classLoader.getResourceAsStream("config.conf").reader())
        val config = ConfigFactory.parseString(configReader.readText())
        configReader.close()

        val packageVersion = CordaConfigurationVersion("corda", 1, 0)
        val componentVersion = CordaConfigurationVersion("corda", 1, 0)
        val configurationKey = CordaConfigurationKey("corda", packageVersion, componentVersion)

        configWriter.updateConfiguration(configurationKey, config)
        verify(publisher, times(1)).publish(any())
    }
}