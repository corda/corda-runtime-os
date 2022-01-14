package net.corda.configuration.read.impl

import com.typesafe.config.ConfigFactory
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactoryImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.kotlin.capture
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ConfigProcessorTest {

    @Captor
    val eventCaptor: ArgumentCaptor<LifecycleEvent> = ArgumentCaptor.forClass(LifecycleEvent::class.java)

    companion object {
        private const val CONFIG_STRING = "{ bar: foo }"
    }

    @Test
    fun `config is forwarded on initial snapshot`() {
        val coordinator = mock<LifecycleCoordinator>()
        val configProcessor = ConfigProcessor(SmartConfigFactoryImpl(), coordinator)
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
        val configProcessor = ConfigProcessor(SmartConfigFactoryImpl(), coordinator)
        val config = Configuration(CONFIG_STRING, "1")
        configProcessor.onNext(Record("topic", "bar", config), null, mapOf("bar" to config))
        verify(coordinator).postEvent(capture(eventCaptor))
        assertThat(eventCaptor.value is NewConfigReceived)
        assertEquals(
            mapOf("bar" to smartConfigFromString(CONFIG_STRING)),
            (eventCaptor.value as NewConfigReceived).config
        )
    }

    private fun smartConfigFromString(string: String): SmartConfig {
        return SmartConfigFactoryImpl().create(ConfigFactory.parseString(string))
    }
}