package net.corda.configuration.read.impl

import com.typesafe.config.ConfigFactory
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class ConfigProcessorTest {

    @Captor
    val eventCaptor: ArgumentCaptor<LifecycleEvent> = ArgumentCaptor.forClass(LifecycleEvent::class.java)

    private val smartConfigFactory = SmartConfigFactory.create(ConfigFactory.empty())

    companion object {
        private const val CONFIG_STRING = "{ bar: foo }"
    }

    @Test
    fun `config is forwarded on initial snapshot`() {
        val coordinator = mock<LifecycleCoordinator>()
        val configProcessor = ConfigProcessor(coordinator, smartConfigFactory)
        val config = Configuration(CONFIG_STRING, "1")
        configProcessor.onSnapshot(mapOf("BAR" to config))
        verify(coordinator).postEvent(capture(eventCaptor))
        assertThat(eventCaptor.value is NewConfigReceived)
        assertEquals(
            mapOf("BAR" to smartConfigFromString(CONFIG_STRING)),
            (eventCaptor.value as NewConfigReceived).config
        )
    }

    @Test
    fun `config is forwarded on update`() {
        val coordinator = mock<LifecycleCoordinator>()
        val configProcessor = ConfigProcessor(coordinator, smartConfigFactory)
        val config = Configuration(CONFIG_STRING, "1")
        configProcessor.onNext(Record("topic", "bar", config), null, mapOf("bar" to config))
        verify(coordinator).postEvent(capture(eventCaptor))
        assertThat(eventCaptor.value is NewConfigReceived)
        assertEquals(
            mapOf("bar" to smartConfigFromString(CONFIG_STRING)),
            (eventCaptor.value as NewConfigReceived).config
        )
    }

    @Test
    fun `no config is forwarded if the snapshot is empty`() {
        val coordinator = mock<LifecycleCoordinator>()
        val configProcessor = ConfigProcessor(coordinator, smartConfigFactory)
        configProcessor.onSnapshot(mapOf())
        verify(coordinator, times(0)).postEvent(any())
    }

    @Test
    fun `no config is forwarded if the update is null`() {
        val coordinator = mock<LifecycleCoordinator>()
        val configProcessor = ConfigProcessor(coordinator, smartConfigFactory)
        val config = Configuration(CONFIG_STRING, "1")
        configProcessor.onNext(Record("topic", "bar", null), null, mapOf("bar" to config))
        verify(coordinator, times(0)).postEvent(any())
    }

    private fun smartConfigFromString(string: String): SmartConfig {
        return SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.parseString(string))
    }
}