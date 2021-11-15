package net.corda.lifecycle.domino.logic

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException

class ConfigurationAwareLeafTileTest {
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { postEvent(any()) } doAnswer {
            handler.lastValue.processEvent(it.getArgument(0) as LifecycleEvent, mock)
        }
    }
    private val factory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val calledNewConfigurations = mutableListOf<Pair<Configuration, Configuration?>>()
    private val registration = mock<AutoCloseable>()
    private val configurationHandler = argumentCaptor<ConfigurationHandler>()
    private val service = mock<ConfigurationReadService> {
        on { registerForUpdates(configurationHandler.capture()) } doReturn registration
    }
    private val configFactory = mock<(Config)-> Configuration> {
        on { invoke(any()) } doAnswer {
            Configuration((it.arguments[0] as Config).getInt(""))
        }
    }
    private val key = "key"

    private data class Configuration(val data: Int)
    private inner class Tile : ConfigurationAwareLeafTile<Configuration>(
        factory,
        service,
        key,
        configFactory,
    ) {
        override fun applyNewConfiguration(
            newConfiguration: Configuration,
            oldConfiguration: Configuration?
        ) {
            calledNewConfigurations.add(newConfiguration to oldConfiguration)
        }
    }

    @Test
    fun `start register listener`() {
        Tile().start()

        verify(service).registerForUpdates(any())
    }

    @Test
    fun `close unregister listener`() {
        val tile = Tile()
        tile.start()

        tile.close()

        verify(registration).close()
    }

    @Test
    fun `close close the coordinator`() {
        val tile = Tile()

        tile.close()

        verify(coordinator).close()
    }

    @Test
    fun `onNewConfiguration will ignore irrelevant changes`() {
        val tile = Tile()
        tile.start()

        configurationHandler.firstValue.onNewConfiguration(emptySet(), mapOf(key to mock()))

        verify(configFactory, never()).invoke(any())
    }

    @Test
    fun `onNewConfiguration will ignore changes with out the actual value`() {
        val tile = Tile()
        tile.start()

        configurationHandler.firstValue.onNewConfiguration(setOf(key), emptyMap())

        verify(configFactory, never()).invoke(any())
    }

    @Test
    fun `onNewConfiguration will invoke error if can not use`() {
        val config = mock<SmartConfig>()
        doThrow(RuntimeException("")).whenever(configFactory).invoke(config)
        val tile = Tile()
        tile.start()

        configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config))

        assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
    }

    @Test
    fun `onNewConfiguration will apply correct configuration if valid`() {
        val config = mock<SmartConfig> {
            on { getInt(any()) } doReturn 33
        }
        val tile = Tile()
        tile.start()

        configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config))

        assertThat(calledNewConfigurations)
            .hasSize(1)
            .contains(Configuration(33) to null)
    }

    @Test
    fun `onNewConfiguration will set the state to running if valid`() {
        val config = mock<SmartConfig> {
            on { getInt(any()) } doReturn 33
        }
        val tile = Tile()
        tile.start()

        configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config))

        assertThat(tile.state).isEqualTo(DominoTile.State.Started)
    }

    @Test
    fun `onNewConfiguration will call only once if the configuration did not changed`() {
        val config1 = mock<SmartConfig> {
            on { getInt(any()) } doReturn 33
        }
        val config2 = mock<SmartConfig> {
            on { getInt(any()) } doReturn 33
        }
        val tile = Tile()
        tile.start()
        configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config1))

        configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config2))

        assertThat(calledNewConfigurations)
            .hasSize(1)
            .contains(Configuration(33) to null)
    }

    @Test
    fun `onNewConfiguration will call on every change`() {
        val config1 = mock<SmartConfig> {
            on { getInt(any()) } doReturn 33
        }
        val config2 = mock<SmartConfig> {
            on { getInt(any()) } doReturn 25
        }
        val tile = Tile()
        tile.start()
        configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config1))

        configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config2))

        assertThat(calledNewConfigurations)
            .hasSize(2)
            .contains(Configuration(33) to null)
            .contains(Configuration(25) to Configuration(33))
    }

    @Test
    fun `onNewConfiguration will not apply new configuration if not started`() {
        val config = mock<SmartConfig> {
            on { getInt(any()) } doReturn 11
        }
        whenever(service.registerForUpdates(any())).doAnswer {
            val handler = it.arguments[0] as ConfigurationHandler
            handler.onNewConfiguration(setOf(key), mapOf(key to config))
            registration
        }
        Tile()

        assertThat(calledNewConfigurations)
            .isEmpty()
    }

    @Test
    fun `onNewConfiguration will re-apply after error`() {
        val badConfig = mock<SmartConfig>()
        val goodConfig = mock<SmartConfig> {
            on { getInt(any()) } doReturn 17
        }
        doThrow(RuntimeException("")).whenever(configFactory).invoke(badConfig)
        val tile = Tile()
        tile.start()

        configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to badConfig))
        configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to goodConfig))

        assertThat(calledNewConfigurations)
            .hasSize(1)
            .contains(Configuration(17) to null)
    }

    @Test
    fun `startTile will apply new configuration when started`() {
        val config = mock<SmartConfig> {
            on { getInt(any()) } doReturn 11
        }
        whenever(service.registerForUpdates(any())).doAnswer {
            val handler = it.arguments[0] as ConfigurationHandler
            handler.onNewConfiguration(setOf(key), mapOf(key to config))
            registration
        }
        val tile = Tile()

        tile.start()

        assertThat(calledNewConfigurations)
            .hasSize(1)
            .contains(Configuration(11) to null)
    }

    @Test
    fun `startTile not will apply new configuration if not ready`() {
        val tile = Tile()

        tile.start()

        assertThat(calledNewConfigurations)
            .isEmpty()
    }

    @Test
    fun `startTile will set as error if tile failed to apply`() {
        val config = mock<SmartConfig> {
            on { getInt(any()) } doReturn 11
        }
        whenever(service.registerForUpdates(any())).doAnswer {
            val handler = it.arguments[0] as ConfigurationHandler
            handler.onNewConfiguration(setOf(key), mapOf(key to config))
            registration
        }
        val tile = object : ConfigurationAwareLeafTile<Configuration>(
            factory,
            service,
            key,
            configFactory,
        ) {
            override fun applyNewConfiguration(
                newConfiguration: Configuration,
                oldConfiguration: Configuration?
            ) {
                throw IOException("")
            }
        }
        tile.start()

        assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
    }

    @Test
    fun `startTile will not register more than once`() {
        val tile = object : ConfigurationAwareLeafTile<Configuration>(
            factory,
            service,
            key,
            configFactory,
        ) {
            override fun applyNewConfiguration(
                newConfiguration: Configuration,
                oldConfiguration: Configuration?
            ) {
            }
        }
        tile.start()

        tile.start()

        verify(service, times(1)).registerForUpdates(any())
    }

    @Test
    fun `stopTile will close the registration`() {
        val tile = object : ConfigurationAwareLeafTile<Configuration>(
            factory,
            service,
            key,
            configFactory,
        ) {
            override fun applyNewConfiguration(
                newConfiguration: Configuration,
                oldConfiguration: Configuration?
            ) {
            }
        }
        tile.start()

        tile.stop()

        verify(registration).close()
    }
}
