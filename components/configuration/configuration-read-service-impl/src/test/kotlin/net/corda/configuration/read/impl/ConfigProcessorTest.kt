package net.corda.configuration.read.impl

import com.typesafe.config.ConfigFactory
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class ConfigProcessorTest {

    @Captor
    val eventCaptor: ArgumentCaptor<LifecycleEvent> = ArgumentCaptor.forClass(LifecycleEvent::class.java)

    private val smartConfigFactory = SmartConfigFactory.create(ConfigFactory.empty())
    private val configMerger: ConfigMerger = mock {
        on { getMessagingConfig(any(), any()) } doAnswer { it.arguments[1] as SmartConfig }
        on { getRPCConfig(any(), any()) } doAnswer { it.arguments[1] as SmartConfig  }
        on { getDbConfig(any(), any()) } doAnswer { it.arguments[1] as SmartConfig  }
        on { getReconciliationConfig(any(), any()) } doAnswer { it.arguments[1] as SmartConfig  }
        on { getCryptoConfig(any(), any()) } doAnswer { it.arguments[1] as SmartConfig  }
    }

    companion object {
        private const val CONFIG_STRING = "{ bar: foo }"
        private const val BOOT_CONFIG_STRING = "{ a: b, b: c }"
        private const val MESSAGING_CONFIG_STRING = "{ b: d }"
        private val schemaVersion = ConfigurationSchemaVersion(1,0)
    }

    @Test
    fun `config is forwarded on initial snapshot`() {
        val coordinator = mock<LifecycleCoordinator>()
        val configProcessor = ConfigProcessor(coordinator, smartConfigFactory, BOOT_CONFIG_STRING.toSmartConfig(), configMerger)
        val config = Configuration(CONFIG_STRING, "1", schemaVersion)
        val messagingConfig = Configuration(MESSAGING_CONFIG_STRING, "1", schemaVersion)
        configProcessor.onSnapshot(mapOf("BAR" to config, MESSAGING_CONFIG to messagingConfig))
        verify(coordinator).postEvent(capture(eventCaptor))
        assertThat(eventCaptor.value is NewConfigReceived)
        assertEquals(CONFIG_STRING.toSmartConfig(), (eventCaptor.value as NewConfigReceived).config["BAR"])
    }

    @Test
    fun `config is forwarded on update`() {
        val coordinator = mock<LifecycleCoordinator>()
        val configProcessor = ConfigProcessor(coordinator, smartConfigFactory, BOOT_CONFIG_STRING.toSmartConfig(), configMerger)
        val config = Configuration(CONFIG_STRING, "1", schemaVersion)
        configProcessor.onNext(Record("topic", "bar", config), null, mapOf("bar" to config))
        verify(coordinator).postEvent(capture(eventCaptor))
        assertThat(eventCaptor.value is NewConfigReceived)
        assertEquals(
            mapOf("bar" to CONFIG_STRING.toSmartConfig()),
            (eventCaptor.value as NewConfigReceived).config
        )
    }

    @Test
    fun `messaging config is forwarded if the snapshot is empty`() {
        val coordinator = mock<LifecycleCoordinator>()
        val bootconfig = BOOT_CONFIG_STRING.toSmartConfig()
        val configProcessor = ConfigProcessor(coordinator, smartConfigFactory, bootconfig, configMerger)
        configProcessor.onSnapshot(mapOf())
        verify(coordinator).postEvent(capture(eventCaptor))
        verify(configMerger, times(1)).getMessagingConfig(bootconfig, null)
    }

    @Test
    fun `no config is forwarded if the update is null`() {
        val coordinator = mock<LifecycleCoordinator>()
        val configProcessor = ConfigProcessor(coordinator, smartConfigFactory, BOOT_CONFIG_STRING.toSmartConfig(), configMerger)
        val config = Configuration(CONFIG_STRING, "1", schemaVersion)
        configProcessor.onNext(Record("topic", "bar", null), null, mapOf("bar" to config))
        verify(coordinator, times(0)).postEvent(any())
    }

    private fun String.toSmartConfig(): SmartConfig {
        return SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.parseString(this))
    }
}