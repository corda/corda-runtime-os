package net.corda.libs.configuration.publish.impl

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.publish.ConfigPublisher
import net.corda.libs.configuration.publish.CordaConfigurationKey
import net.corda.libs.configuration.publish.CordaConfigurationVersion
import net.corda.messaging.api.publisher.Publisher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.io.BufferedReader

class ConfigPublisherImplTest {
    private lateinit var configPublisher: ConfigPublisher
    private var publisher: Publisher = mock()

    @BeforeEach
    fun beforeEach() {
        configPublisher = ConfigPublisherImpl("topic", publisher)
    }

    @Test
    fun testUpdateConfiguration() {
        val configReader = BufferedReader(this::class.java.classLoader.getResourceAsStream("config.conf").reader())
        val config = ConfigFactory.parseString(configReader.readText())
        configReader.close()

        val packageVersion = CordaConfigurationVersion("corda", 1, 0)
        val componentVersion = CordaConfigurationVersion("corda", 1, 0)
        val configurationKey = CordaConfigurationKey("corda", packageVersion, componentVersion)

        configPublisher.updateConfiguration(configurationKey, config)
        verify(publisher, times(1)).publish(any())
    }
}