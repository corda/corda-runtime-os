package net.corda.configuration.read.impl

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.libs.configuration.SmartConfigFactoryFactory
import net.corda.lifecycle.LifecycleCoordinator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class ComponentConfigHandlerTest {

    companion object {
        private const val FOO_KEY = "foo"
        private const val BAR_KEY = "bar"
        private const val BAZ_KEY = "baz"
    }

    private val smartConfigFactory = SmartConfigFactoryFactory.createWithoutSecurityServices()
    private val fooConfig = smartConfigFactory.create(ConfigFactory.parseMap(mapOf("foo" to 1)))
    private val barConfig = smartConfigFactory.create(ConfigFactory.parseMap(mapOf("bar" to 2)))
    private val bazConfig = smartConfigFactory.create(ConfigFactory.parseMap(mapOf("baz" to 3)))

    @Test
    fun `posts only when all required config available`() {
        val coordinator = mock<LifecycleCoordinator>()
        val handler = ComponentConfigHandler(coordinator, setOf(FOO_KEY, BAR_KEY))
        handler.onNewConfiguration(setOf(FOO_KEY), mapOf(FOO_KEY to fooConfig))
        verify(coordinator, times(0)).postEvent(any())
        handler.onNewConfiguration(setOf(BAR_KEY), mapOf(FOO_KEY to fooConfig, BAR_KEY to barConfig))
        verify(coordinator).postEvent(
            ConfigChangedEvent(
                setOf(FOO_KEY, BAR_KEY),
                mapOf(FOO_KEY to fooConfig, BAR_KEY to barConfig)
            )
        )
    }

    @Test
    fun `posts any changes to required config`() {
        val coordinator = mock<LifecycleCoordinator>()
        val handler = ComponentConfigHandler(coordinator, setOf(FOO_KEY, BAR_KEY))
        handler.onNewConfiguration(setOf(FOO_KEY, BAR_KEY), mapOf(FOO_KEY to fooConfig, BAR_KEY to barConfig))
        verify(coordinator).postEvent(
            ConfigChangedEvent(
                setOf(FOO_KEY, BAR_KEY),
                mapOf(FOO_KEY to fooConfig, BAR_KEY to barConfig)
            )
        )
        handler.onNewConfiguration(setOf(FOO_KEY), mapOf(FOO_KEY to fooConfig, BAR_KEY to barConfig))
        verify(coordinator).postEvent(
            ConfigChangedEvent(
                setOf(FOO_KEY),
                mapOf(FOO_KEY to fooConfig, BAR_KEY to barConfig)
            )
        )
    }

    @Test
    fun `does not post irrelevant config updates`() {
        val coordinator = mock<LifecycleCoordinator>()
        val handler = ComponentConfigHandler(coordinator, setOf(FOO_KEY, BAR_KEY))
        handler.onNewConfiguration(setOf(FOO_KEY, BAR_KEY), mapOf(FOO_KEY to fooConfig, BAR_KEY to barConfig))
        verify(coordinator).postEvent(
            ConfigChangedEvent(
                setOf(FOO_KEY, BAR_KEY),
                mapOf(FOO_KEY to fooConfig, BAR_KEY to barConfig)
            )
        )
        clearInvocations(coordinator)
        handler.onNewConfiguration(
            setOf(BAZ_KEY),
            mapOf(FOO_KEY to fooConfig, BAR_KEY to barConfig, BAZ_KEY to bazConfig)
        )
        verify(coordinator, times(0)).postEvent(any())
    }

    @Test
    fun `filters config to just include relevant config`() {
        val coordinator = mock<LifecycleCoordinator>()
        val handler = ComponentConfigHandler(coordinator, setOf(FOO_KEY, BAR_KEY))
        handler.onNewConfiguration(
            setOf(FOO_KEY, BAR_KEY, BAZ_KEY),
            mapOf(FOO_KEY to fooConfig, BAR_KEY to barConfig, BAZ_KEY to bazConfig)
        )
        verify(coordinator).postEvent(
            ConfigChangedEvent(
                setOf(FOO_KEY, BAR_KEY),
                mapOf(FOO_KEY to fooConfig, BAR_KEY to barConfig)
            )
        )
    }
}