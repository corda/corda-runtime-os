package net.corda.lifecycle.domino.logic

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
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

class DominoTileTest {

    companion object {
        private const val TILE_NAME = "MyTestTile"
    }

    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { postEvent(any()) } doAnswer {
            handler.lastValue.processEvent(it.getArgument(0) as LifecycleEvent, mock)
        }
    }
    private val factory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }

    @Nested
    inner class SimpleLeafTileTests {

        fun tile(): DominoTile {
            return DominoTile(TILE_NAME, factory)
        }

        @Test
        fun `start will update the status to started`() {
            val tile = tile()

            tile.start()

            assertThat(tile.state).isEqualTo(DominoTile.State.Started)
        }

        @Test
        fun `start will start the coordinator if not started`() {
            val tile = tile()

            tile.start()

            verify(coordinator).start()
        }


        @Test
        fun `stop will update the status to stopped`() {
            val tile = tile()
            tile.stop()

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedByParent)
        }

        @Test
        fun `stop will update the status the tile is started`() {
            val tile = tile()
            tile.start()

            tile.stop()

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedByParent)
        }

        @Test
        fun `isRunning will return true if state is started`() {
            val tile = tile()
            tile.start()

            assertThat(tile.isRunning).isTrue
        }

        @Test
        fun `stop will update the coordinator state from up to down`() {
            val tile = tile()
            tile.start()

            tile.stop()

            verify(coordinator).updateStatus(LifecycleStatus.DOWN)
        }

        @Test
        fun `processEvent will set as error if the event is error`() {
            val tile = tile()
            tile.start()

            handler.lastValue.processEvent(ErrorEvent(Exception("")), coordinator)

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
        }

//        TODO: We can't verify this anymore (what we should instead verify that children/external components don't start/stop
//        When this happens
//    @Test
//    fun `processEvent will not start the tile the second time`() {
//        val tile = Tile()
//        tile.start()
//
//        handler.lastValue.processEvent(StartEvent(), coordinator)
//
//        assertThat(tile.started).isEqualTo(0)
//    }

        @Test
        fun `handleEvent can handle unknown event`() {
            val tile = tile()
            val event = RegistrationStatusChangeEvent(
                mock(),
                LifecycleStatus.UP
            )
            tile.start()

            assertDoesNotThrow {
                handler.lastValue.processEvent(event, coordinator)
            }
        }

        @Test
        fun `processEvent will ignore the stop event`() {
            tile()

            handler.lastValue.processEvent(StopEvent(), coordinator)
        }

        @Test
        fun `processEvent will ignore unexpected event`() {
            tile()

            handler.lastValue.processEvent(
                object : LifecycleEvent {},
                coordinator
            )
        }

        @Test
        fun `close will not change the tiles state`() {
            val tile = tile()

            tile.close()

            assertThat(tile.state).isEqualTo(DominoTile.State.Created)
        }

        @Test
        fun `close will close the coordinator`() {
            val tile = tile()

            tile.close()

            verify(coordinator).close()
        }

        @Test
        fun `withLifecycleLock return the invocation value`() {
            val tile = tile()

            val data = tile.withLifecycleLock {
                33
            }

            assertThat(data).isEqualTo(33)
        }
    }

    @Nested
    inner class LeafTileWithResourcesTests {

        @Test
        fun `startTile called createResource`() {
            var createResourceCalled = 0
            @Suppress("UNUSED_PARAMETER")
            fun createResources(resources: ResourcesHolder) {
                createResourceCalled ++
            }

            val tile = DominoTile(TILE_NAME, factory, ::createResources)
            tile.start()

            assertThat(createResourceCalled).isEqualTo(1)
        }

        @Test
        fun `startTile will set error if created resource failed`() {
            @Suppress("UNUSED_PARAMETER")
            fun createResources(resources: ResourcesHolder) {
                throw IOException("")
            }
            val tile = DominoTile(TILE_NAME, factory, ::createResources)
            tile.start()

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
        }

        @Test
        fun `startTile will not set started until resources are created`() {
            @Suppress("UNUSED_PARAMETER")
            fun createResources(resources: ResourcesHolder) { }
            val tile = DominoTile(TILE_NAME, factory, ::createResources)
            tile.start()

            assertThat(tile.state).isEqualTo(DominoTile.State.Created)
            tile.resourcesStarted(false)
            assertThat(tile.state).isEqualTo(DominoTile.State.Started)
        }

        @Test
        fun `if an error occurs when resources are created the the the tile will error`() {
            @Suppress("UNUSED_PARAMETER")
            fun createResources(resources: ResourcesHolder) { }
            val tile = DominoTile(TILE_NAME, factory, ::createResources)
            tile.start()

            assertThat(tile.state).isEqualTo(DominoTile.State.Created)
            tile.resourcesStarted(true)
            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
        }

        @Test
        fun `startTile, stopTile, startTile will not restart the tile until resources are created`() {
            @Suppress("UNUSED_PARAMETER")
            fun createResources(resources: ResourcesHolder) { }
            val tile = DominoTile(TILE_NAME, factory, ::createResources)
            tile.start()
            tile.stop()

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedByParent)
            tile.start()
            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedByParent)
            tile.resourcesStarted(false)
            assertThat(tile.state).isEqualTo(DominoTile.State.Started)
        }

        @Test
        fun `stopTile will close all the resources`() {
            val actions = mutableListOf<Int>()
            @Suppress("UNUSED_PARAMETER")
            fun createResources(resources: ResourcesHolder) {
                resources.keep {
                    actions.add(1)
                }
                resources.keep {
                    actions.add(2)
                }
                resources.keep {
                    actions.add(3)
                }
            }
            val tile = DominoTile(TILE_NAME, factory, ::createResources)
            tile.start()
            tile.stop()

            assertThat(actions).isEqualTo(listOf(3, 2, 1))
        }

        @Test
        fun `stopTile will ignore error during stop`() {
            val actions = mutableListOf<Int>()

            fun createResources(resources: ResourcesHolder) {
                resources.keep {
                    actions.add(1)
                }
                resources.keep {
                    throw IOException("")
                }
                resources.keep {
                    actions.add(3)
                }
            }
            val tile = DominoTile(TILE_NAME, factory, ::createResources)
            tile.start()
            tile.stop()

            assertThat(actions).isEqualTo(listOf(3, 1))
        }

        @Test
        fun `stopTile will not run the same actions again`() {
            val actions = mutableListOf<Int>()
            fun createResources(resources: ResourcesHolder) {
                resources.keep {
                    actions.add(1)
                }
            }
            val tile = DominoTile(TILE_NAME, factory, ::createResources)

            tile.start()
            tile.stop()
            tile.stop()
            tile.stop()

            assertThat(actions).isEqualTo(listOf(1))
        }
    }

    private data class Configuration(val data: Int)

    @Nested
    inner class LeafTileWithConfigTests {

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

        private inner class TileConfigurationChangeHandler : ConfigurationChangeHandler<Configuration>(
            service,
            key,
            configFactory,
        ) {
            override fun applyNewConfiguration(
                newConfiguration: Configuration,
                oldConfiguration: Configuration?,
                resources: ResourcesHolder
            ) {
                calledNewConfigurations.add(newConfiguration to oldConfiguration)
            }
        }

        fun tile(): DominoTile {
            return DominoTile(TILE_NAME, factory, configurationChangeHandler = TileConfigurationChangeHandler())
        }

        @Test
        fun `start register listener`() {
            tile().start()

            verify(service).registerForUpdates(any())
        }

        @Test
        fun `close unregister listener`() {
            val tile = tile()
            tile.start()
            tile.close()

            verify(registration).close()
        }

        @Test
        fun `close close the coordinator`() {
            val tile = tile()

            tile.close()

            verify(coordinator).close()
        }

        @Test
        fun `onNewConfiguration will ignore irrelevant changes`() {
            val tile = tile()
            tile.start()

            configurationHandler.firstValue.onNewConfiguration(emptySet(), mapOf(key to mock()))

            verify(configFactory, never()).invoke(any())
        }

        @Test
        fun `onNewConfiguration will ignore changes with out the actual value`() {
            val tile = tile()
            tile.start()

            configurationHandler.firstValue.onNewConfiguration(setOf(key), emptyMap())

            verify(configFactory, never()).invoke(any())
        }

        @Test
        fun `onNewConfiguration will cause the tile to stop if bad config`() {
            val config = mock<Config>()
            val tile = tile()
            tile.start()

            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config))
            tile.configApplied(DominoTile.ConfigUpdateResult.Error(java.lang.RuntimeException("Bad config")))
            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToBadConfig)
            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config))

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToBadConfig)
            assertThat(tile.isRunning).isFalse
        }

        @Test
        fun `onNewConfiguration will apply correct configuration if valid`() {
            val config = mock<Config> {
                on { getInt(any()) } doReturn 33
            }
            val tile = tile()
            tile.start()

            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config))

            assertThat(calledNewConfigurations)
                .hasSize(1)
                .contains(Configuration(33) to null)
        }

        @Test
        fun `onNewConfiguration will set the state to started if valid`() {
            val config = mock<Config> {
                on { getInt(any()) } doReturn 33
            }
            val tile = tile()
            tile.start()

            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config))

            assertThat(tile.state).isEqualTo(DominoTile.State.Created)
            tile.configApplied(DominoTile.ConfigUpdateResult.Success)
            assertThat(tile.state).isEqualTo(DominoTile.State.Started)
            assertThat(tile.isRunning).isTrue
        }

        @Test
        fun `onNewConfiguration will call only once if the configuration did not changed`() {
            val config1 = mock<Config> {
                on { getInt(any()) } doReturn 33
            }
            val config2 = mock<Config> {
                on { getInt(any()) } doReturn 33
            }
            val tile = tile()
            tile.start()
            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config1))

            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config2))

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
            val tile = tile()
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
            val config = mock<Config> {
                on { getInt(any()) } doReturn 11
            }
            whenever(service.registerForUpdates(any())).doAnswer {
                val handler = it.arguments[0] as ConfigurationHandler
                handler.onNewConfiguration(setOf(key), mapOf(key to config))
                registration
            }
            tile()

            assertThat(calledNewConfigurations)
                .isEmpty()
        }

        @Test
        fun `onNewConfiguration will re-apply after error`() {
            val badConfig = mock<Config> {
                on { getInt(any()) } doReturn 5
            }
            val goodConfig = mock<Config> {
                on { getInt(any()) } doReturn 17
            }

            val tile = tile()
            tile.start()
            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to badConfig))
            tile.configApplied(DominoTile.ConfigUpdateResult.Error(java.lang.RuntimeException("Bad config")))
            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToBadConfig)

            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to goodConfig))
            tile.configApplied(DominoTile.ConfigUpdateResult.Success)
            assertThat(tile.state).isEqualTo(DominoTile.State.Started)

            assertThat(calledNewConfigurations).contains(Configuration(17) to Configuration(5))
        }

        @Test
        fun `if config is the same after error then the tile stays stopped`() {
            val badConfig = mock<Config> {
                on { getInt(any()) } doReturn 5
            }

            val tile = tile()
            tile.start()
            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to badConfig))
            tile.configApplied(DominoTile.ConfigUpdateResult.Error(java.lang.RuntimeException("Bad config")))
            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToBadConfig)

            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to badConfig))
            tile.configApplied(DominoTile.ConfigUpdateResult.NoUpdate)
            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToBadConfig)

            assertThat(calledNewConfigurations).hasSize(1)
            assertThat(tile.isRunning).isFalse
        }

        @Test
        fun `startTile will apply new configuration when started`() {
            val config = mock<Config> {
                on { getInt(any()) } doReturn 11
            }
            whenever(service.registerForUpdates(any())).doAnswer {
                val handler = it.arguments[0] as ConfigurationHandler
                handler.onNewConfiguration(setOf(key), mapOf(key to config))
                registration
            }
            val tile = tile()

            tile.start()

            assertThat(calledNewConfigurations)
                .hasSize(1)
                .contains(Configuration(11) to null)
        }

        @Test
        fun `startTile not will apply new configuration if not ready`() {
            val tile = tile()

            tile.start()

            assertThat(calledNewConfigurations)
                .isEmpty()
        }

        @Test
        fun `startTile will not register more than once`() {
            val tile = tile()

            tile.start()

            tile.start()

            verify(service, times(1)).registerForUpdates(any())
        }

        @Test
        fun `stopTile will close the registration`() {
            val tile = tile()
            tile.start()

            tile.stop()

            verify(registration).close()
        }
    }
}
