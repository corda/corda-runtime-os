package net.corda.configuration.read.impl

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.configuration.ConfigKeys.Companion.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.Companion.FLOW_CONFIG
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.CountDownLatch

@ExtendWith(ServiceExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfigurationReadServiceImplTest {

    companion object {
        private const val BOOT_CONFIG_STRING = """
            {}
        """
    }

    @InjectService(timeout = 4000)
    lateinit var configurationReadService: ConfigurationReadService

    @InjectService(timeout = 4000)
    lateinit var lifecycleRegistry: LifecycleRegistry

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    private val smartConfigFactory = SmartConfigFactory.create(ConfigFactory.empty())

    @Test
    fun `config read service posts initial event once bootstrap config is provided`() {
        val bootConfig = smartConfigFactory.create(ConfigFactory.parseString(BOOT_CONFIG_STRING))
        val latch = CountDownLatch(1)
        configurationReadService.start()
        configurationReadService.bootstrapConfig(bootConfig)
        val expectedKeys = mutableSetOf(BOOT_CONFIG)
        val expectedConfig = mutableMapOf(BOOT_CONFIG to bootConfig)
        var receivedKeys = emptySet<String>()
        var receivedConfig = mapOf<String, SmartConfig>()
        val reg = configurationReadService.registerForUpdates { keys, config ->
            receivedKeys = keys
            receivedConfig = config
            latch.countDown()
        }
        latch.await()
        // Note that this does not wait for the connection to be made to the message bus, so there is no guarantee that
        // the component has marked itself up yet.
        assertEquals(expectedKeys, receivedKeys, "Incorrect keys")
        assertEquals(expectedConfig, receivedConfig, "Incorrect config")
        reg.close()
        configurationReadService.close()
    }

    @Test
    fun `config read service delivers configuration updates to clients`() {
        val bootConfig = smartConfigFactory.create(ConfigFactory.parseString(BOOT_CONFIG_STRING))
        var latch = CountDownLatch(1)
        configurationReadService.start()
        configurationReadService.bootstrapConfig(bootConfig)
        val publisher = publisherFactory.createPublisher(PublisherConfig("foo"))
        val expectedKeys = mutableSetOf(BOOT_CONFIG)
        val expectedConfig = mutableMapOf(BOOT_CONFIG to bootConfig)
        var receivedKeys = emptySet<String>()
        var receivedConfig = mapOf<String, SmartConfig>()
        val reg = configurationReadService.registerForUpdates { keys, config ->
            receivedKeys = keys
            receivedConfig = config
            latch.countDown()
        }
        latch.await()
        assertEquals(expectedKeys, receivedKeys, "Incorrect keys")
        assertEquals(expectedConfig, receivedConfig, "Incorrect config")
        latch = CountDownLatch(1)

        // Publish new configuration and verify it gets delivered
        val flowConfig = smartConfigFactory.create(ConfigFactory.parseMap(mapOf("foo" to "bar")))
        expectedKeys.clear()
        expectedKeys.add(FLOW_CONFIG)
        expectedConfig[FLOW_CONFIG] = flowConfig
        publisher.publish(listOf(Record(CONFIG_TOPIC, FLOW_CONFIG, Configuration("{ foo: bar }", "1"))))
        latch.await()
        assertEquals(expectedKeys, receivedKeys, "Incorrect keys")
        assertEquals(expectedConfig, receivedConfig, "Incorrect config")
        assertEquals(
            LifecycleStatus.UP,
            lifecycleRegistry.componentStatus()[LifecycleCoordinatorName.forComponent<ConfigurationReadService>()]?.status
        )

        // Cleanup
        reg.close()
        publisher.close()
        configurationReadService.close()
    }

    @Test
    fun `when a client registers all current configuration is delivered to the client`() {
        val bootConfig = smartConfigFactory.create(ConfigFactory.parseString(BOOT_CONFIG_STRING))
        val latch = CountDownLatch(1)
        val prepareLatch = CountDownLatch(1)
        configurationReadService.start()
        configurationReadService.bootstrapConfig(bootConfig)

        // Publish flow config and wait until it has been received by the service
        val flowConfig = smartConfigFactory.create(ConfigFactory.parseMap(mapOf("foo" to "bar")))
        val publisher = publisherFactory.createPublisher(PublisherConfig("foo"))
        publisher.publish(listOf(Record(CONFIG_TOPIC, FLOW_CONFIG, Configuration("{ foo: bar }", "1"))))
        val reg1 = configurationReadService.registerForUpdates { keys, _ ->
            if (keys.contains(FLOW_CONFIG)) {
                prepareLatch.countDown()
            }
        }
        prepareLatch.await()
        assertEquals(
            LifecycleStatus.UP,
            lifecycleRegistry.componentStatus()[LifecycleCoordinatorName.forComponent<ConfigurationReadService>()]?.status
        )

        // Register and verify everything gets delivered
        val expectedKeys = mutableSetOf(BOOT_CONFIG, FLOW_CONFIG)
        val expectedConfig = mutableMapOf(BOOT_CONFIG to bootConfig, FLOW_CONFIG to flowConfig)
        var receivedKeys = emptySet<String>()
        var receivedConfig = mapOf<String, SmartConfig>()
        val reg = configurationReadService.registerForUpdates { keys, config ->
            receivedKeys = keys
            receivedConfig = config
            latch.countDown()
        }
        latch.await()
        assertEquals(expectedKeys, receivedKeys, "Incorrect keys")
        assertEquals(expectedConfig, receivedConfig, "Incorrect config")

        // Cleanup
        reg.close()
        reg1.close()
        publisher.close()
        configurationReadService.close()
    }
}