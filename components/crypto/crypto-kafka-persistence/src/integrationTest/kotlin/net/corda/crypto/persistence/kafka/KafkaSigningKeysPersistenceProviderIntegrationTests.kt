package net.corda.crypto.persistence.kafka

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.persistence.SigningKeysPersistenceProvider
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.eventually
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.UUID

@ExtendWith(ServiceExtension::class)
class KafkaSigningKeysPersistenceProviderIntegrationTests {
    companion object {
        const val CLIENT_ID = "crypto-kafka-persistence-signing-integration-test"

        private fun Lifecycle.stopAndWait() {
            stop()
            isStopped()
        }

        private fun Lifecycle.startAndWait() {
            start()
            isStarted()
        }

        private fun Lifecycle.isStopped() = eventually {
            assertFalse(isRunning, "Failed waiting to stop for ${this::class.java.name}")
        }

        private fun Lifecycle.isStarted() = eventually {
            assertTrue(isRunning, "Failed waiting to start for ${this::class.java.name}")
        }
    }

    @InjectService(timeout = 5000L)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 5000L)
    lateinit var configurationReadService: ConfigurationReadService

    @InjectService(timeout = 5000L)
    lateinit var provider: SigningKeysPersistenceProvider

    private val cryptoConfig: String = "{}"

    private val bootConf = """
        instanceId=1
    """

    private fun Publisher.publishConf(key: String, config: String) =
        publish(listOf(Record(Schemas.Config.CONFIG_TOPIC, key, Configuration(config, "1"))))

    @BeforeEach
    fun setup() {
        // start all required services
        configurationReadService.startAndWait()
        provider.startAndWait()

        // Publish crypto config
        with(publisherFactory.createPublisher(PublisherConfig(CLIENT_ID))) {
            publishConf(ConfigKeys.CRYPTO_CONFIG, cryptoConfig)
        }

        // Set basic bootstrap config
        configurationReadService.bootstrapConfig(
            SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.parseString(bootConf))
        )
    }

    @Test
    @Timeout(30)
    fun `Should return instances using same processor instance regardless of tenant`() {
        assertTrue(provider.isRunning)
        eventually {
            assertNotNull(provider.getInstance(UUID.randomUUID().toString()) { it })
        }
        for (i in 1..100) {
            assertNotNull(provider.getInstance(UUID.randomUUID().toString()) { it })
        }
        provider.stopAndWait()
        assertFalse(provider.isRunning)
    }
}