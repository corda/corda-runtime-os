package net.corda.p2p.gateway.domino

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.StartEvent
import net.corda.p2p.gateway.domino.ConfigurationAwareTile.Companion.CONFIG_KEY
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException

class ConfigurationAwareTileTest {
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { start() } doAnswer {
            handler.lastValue.processEvent(StartEvent(), mock)
        }
    }
    private val factory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val calledNewConfigurations = mutableListOf<Pair<Configuration, Configuration?>>()
    private val registration = mock<AutoCloseable>()
    private val service = mock<ConfigurationReadService> {
        on { registerForUpdates(any()) } doReturn registration
    }
    private val configFactory = mock<(Config)->Configuration> {
        on { invoke(any()) } doAnswer {
            Configuration((it.arguments[0] as Config).getInt(""))
        }
    }

    private data class Configuration(val data: Int)
    private inner class Tile : ConfigurationAwareTile<Configuration>(
        factory,
        service,
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
    fun `init register listener`() {
        val tile = Tile()

        verify(service).registerForUpdates(tile)
    }

    @Test
    fun `close unregister listener`() {
        val tile = Tile()

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

        tile.onNewConfiguration(emptySet(), mapOf(CONFIG_KEY to mock()))

        verify(configFactory, never()).invoke(any())
    }

    @Test
    fun `onNewConfiguration will ignore changes with out the actual value`() {
        val tile = Tile()
        tile.start()

        tile.onNewConfiguration(setOf(CONFIG_KEY), emptyMap())

        verify(configFactory, never()).invoke(any())
    }

    @Test
    fun `onNewConfiguration will invoke error if can not use`() {
        val config = mock<Config>()
        doThrow(RuntimeException("")).whenever(configFactory).invoke(config)
        val tile = Tile()
        tile.start()

        tile.onNewConfiguration(setOf(CONFIG_KEY), mapOf(CONFIG_KEY to config))

        assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
    }

    @Test
    fun `onNewConfiguration will apply correct configuration if valid`() {
        val config = mock<Config> {
            on { getInt(any()) } doReturn 33
        }
        val tile = Tile()
        tile.start()

        tile.onNewConfiguration(setOf(CONFIG_KEY), mapOf(CONFIG_KEY to config))

        assertThat(calledNewConfigurations)
            .hasSize(1)
            .contains(Configuration(33) to null)
    }

    @Test
    fun `onNewConfiguration will set the state to running if valid`() {
        val config = mock<Config> {
            on { getInt(any()) } doReturn 33
        }
        val tile = Tile()
        tile.start()

        tile.onNewConfiguration(setOf(CONFIG_KEY), mapOf(CONFIG_KEY to config))

        assertThat(tile.state).isEqualTo(DominoTile.State.Started)
    }

    @Test
    fun `onNewConfiguration will call only once if the configuration did not changed`() {
        val config1 = mock<Config> {
            on { getInt(any()) } doReturn 33
        }
        val config2 = mock<Config> {
            on { getInt(any()) } doReturn 33
        }
        val tile = Tile()
        tile.start()
        tile.onNewConfiguration(setOf(CONFIG_KEY), mapOf(CONFIG_KEY to config1))

        tile.onNewConfiguration(setOf(CONFIG_KEY), mapOf(CONFIG_KEY to config2))

        assertThat(calledNewConfigurations)
            .hasSize(1)
            .contains(Configuration(33) to null)
    }

    @Test
    fun `onNewConfiguration will call on every change`() {
        val config1 = mock<Config> {
            on { getInt(any()) } doReturn 33
        }
        val config2 = mock<Config> {
            on { getInt(any()) } doReturn 25
        }
        val tile = Tile()
        tile.start()
        tile.onNewConfiguration(setOf(CONFIG_KEY), mapOf(CONFIG_KEY to config1))

        tile.onNewConfiguration(setOf(CONFIG_KEY), mapOf(CONFIG_KEY to config2))

        assertThat(calledNewConfigurations)
            .hasSize(2)
            .contains(Configuration(33) to null)
            .contains(Configuration(25) to Configuration(33))
    }

    @Test
    fun `onNewConfiguration will not apply new configuration if not started`() {
        val config = mock<Config> {
            on { getInt(any()) } doReturn 11
        }
        val tile = Tile()

        tile.onNewConfiguration(setOf(CONFIG_KEY), mapOf(CONFIG_KEY to config))

        assertThat(calledNewConfigurations)
            .isEmpty()
    }

    @Test
    fun `onNewConfiguration will re-apply after error`() {
        val badConfig = mock<Config>()
        val goodConfig = mock<Config> {
            on { getInt(any()) } doReturn 17
        }
        doThrow(RuntimeException("")).whenever(configFactory).invoke(badConfig)
        val tile = Tile()
        tile.start()

        tile.onNewConfiguration(setOf(CONFIG_KEY), mapOf(CONFIG_KEY to badConfig))
        tile.onNewConfiguration(setOf(CONFIG_KEY), mapOf(CONFIG_KEY to goodConfig))

        assertThat(calledNewConfigurations)
            .hasSize(1)
            .contains(Configuration(17) to null)
    }

    @Test
    fun `createResources will apply new configuration when started`() {
        val config = mock<Config> {
            on { getInt(any()) } doReturn 11
        }
        val tile = Tile()
        tile.onNewConfiguration(setOf(CONFIG_KEY), mapOf(CONFIG_KEY to config))

        tile.start()

        assertThat(calledNewConfigurations)
            .hasSize(1)
            .contains(Configuration(11) to null)
    }

    @Test
    fun `createResources not will apply new configuration if not ready`() {
        val tile = Tile()

        tile.start()

        assertThat(calledNewConfigurations)
            .isEmpty()
    }

    @Test
    fun `createResources will set as error if tile failed to apply`() {
        val config = mock<Config> {
            on { getInt(any()) } doReturn 11
        }
        val tile = object : ConfigurationAwareTile<Configuration>(
            factory,
            service,
            configFactory,
        ) {
            override fun applyNewConfiguration(
                newConfiguration: Configuration,
                oldConfiguration: Configuration?
            ) {
                throw IOException("")
            }
        }
        tile.onNewConfiguration(setOf(CONFIG_KEY), mapOf(CONFIG_KEY to config))

        tile.start()

        assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
    }
}
