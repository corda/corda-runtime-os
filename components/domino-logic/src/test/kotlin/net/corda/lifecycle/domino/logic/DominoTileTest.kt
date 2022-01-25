package net.corda.lifecycle.domino.logic

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.CustomEvent
import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

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

        private fun tile(): DominoTile {
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
        fun `processEvent will ignore the start event`() {
            tile()

            handler.lastValue.processEvent(StartEvent(), coordinator)
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

        @Test
        fun `close tile will not be able to start`() {
            val tile = tile()
            tile.close()

            tile.start()

            assertThat(tile.state).isNotEqualTo(DominoTile.State.Started)
        }
    }

    @Nested
    inner class LeafTileWithResourcesTests {

        @Test
        fun `startTile called createResource`() {
            var createResourceCalled = 0
            @Suppress("UNUSED_PARAMETER")
            fun createResources(resources: ResourcesHolder): CompletableFuture<Unit> {
                createResourceCalled++
                return CompletableFuture()
            }

            val tile = DominoTile(TILE_NAME, factory, ::createResources)
            tile.start()

            assertThat(createResourceCalled).isEqualTo(1)
        }

        @Test
        fun `startTile will set error if created resource failed`() {
            @Suppress("UNUSED_PARAMETER")
            fun createResources(resources: ResourcesHolder): CompletableFuture<Unit> {
                val future = CompletableFuture<Unit>()
                future.completeExceptionally(IOException(""))
                return future
            }
            val tile = DominoTile(TILE_NAME, factory, ::createResources)
            tile.start()

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
        }

        @Test
        fun `startTile will not set started until resources are created`() {
            val future: CompletableFuture<Unit> = CompletableFuture<Unit>()
            @Suppress("UNUSED_PARAMETER")
            fun createResources(resources: ResourcesHolder): CompletableFuture<Unit> {
                return future
            }
            val tile = DominoTile(TILE_NAME, factory, ::createResources)
            tile.start()

            assertThat(tile.state).isEqualTo(DominoTile.State.Created)
            future.complete(Unit)
            assertThat(tile.state).isEqualTo(DominoTile.State.Started)
        }

        @Test
        fun `if an error occurs when resources are created the the the tile will error`() {
            val future: CompletableFuture<Unit> = CompletableFuture<Unit>()
            @Suppress("UNUSED_PARAMETER")
            fun createResources(resources: ResourcesHolder): CompletableFuture<Unit> {
                return future
            }
            val tile = DominoTile(TILE_NAME, factory, ::createResources)
            tile.start()

            assertThat(tile.state).isEqualTo(DominoTile.State.Created)
            future.completeExceptionally(RuntimeException("Ohh no"))
            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
        }

        @Test
        fun `startTile, stopTile, startTile will not restart the tile until resources are created`() {
            val future: CompletableFuture<Unit> = CompletableFuture<Unit>()
            @Suppress("UNUSED_PARAMETER")
            fun createResources(resources: ResourcesHolder): CompletableFuture<Unit> {
                return future
            }
            val tile = DominoTile(TILE_NAME, factory, ::createResources)
            tile.start()
            tile.stop()

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedByParent)
            tile.start()
            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedByParent)
            future.complete(Unit)
            assertThat(tile.state).isEqualTo(DominoTile.State.Started)
        }

        @Test
        fun `stopTile will close all the resources`() {
            val actions = mutableListOf<Int>()
            @Suppress("UNUSED_PARAMETER")
            fun createResources(resources: ResourcesHolder): CompletableFuture<Unit> {
                resources.keep {
                    actions.add(1)
                }
                resources.keep {
                    actions.add(2)
                }
                resources.keep {
                    actions.add(3)
                }
                return CompletableFuture()
            }
            val tile = DominoTile(TILE_NAME, factory, ::createResources)
            tile.start()
            tile.stop()

            assertThat(actions).isEqualTo(listOf(3, 2, 1))
        }

        @Test
        fun `stopTile will ignore error during stop`() {
            val actions = mutableListOf<Int>()
            @Suppress("UNUSED_PARAMETER")
            fun createResources(resources: ResourcesHolder): CompletableFuture<Unit> {
                resources.keep {
                    actions.add(1)
                }
                resources.keep {
                    throw IOException("")
                }
                resources.keep {
                    actions.add(3)
                }
                return CompletableFuture()
            }
            val tile = DominoTile(TILE_NAME, factory, ::createResources)
            tile.start()
            tile.stop()

            assertThat(actions).isEqualTo(listOf(3, 1))
        }

        @Test
        fun `stopTile will not run the same actions again`() {
            val actions = mutableListOf<Int>()
            @Suppress("UNUSED_PARAMETER")
            fun createResources(resources: ResourcesHolder): CompletableFuture<Unit> {
                resources.keep {
                    actions.add(1)
                }
                return CompletableFuture()
            }
            val tile = DominoTile(TILE_NAME, factory, ::createResources)

            tile.start()
            tile.stop()
            tile.stop()
            tile.stop()

            assertThat(actions).isEqualTo(listOf(1))
        }

        @Test
        fun `second start will not restart anything`() {
            val called = AtomicInteger(0)
            val tile = DominoTile(TILE_NAME, factory, createResources = {
                called.incrementAndGet()
                val future = CompletableFuture<Unit>()
                future.complete(Unit)
                future
            })
            tile.start()

            tile.start()

            assertThat(called).hasValue(1)
        }

        @Test
        fun `second start will not recreate the resources if it had errors`() {
            val called = AtomicInteger(0)
            val tile = DominoTile(TILE_NAME, factory, createResources = {
                called.incrementAndGet()
                val future = CompletableFuture<Unit>()
                future.completeExceptionally(RuntimeException("Ohh no"))
                future
            })
            tile.start()

            tile.start()

            assertThat(called).hasValue(1)
        }

        @Test
        fun `second start will recreate the resources if it was stopped`() {
            val called = AtomicInteger(0)
            val tile = DominoTile(TILE_NAME, factory, createResources = {
                called.incrementAndGet()
                CompletableFuture()
            })

            tile.start()
            tile.stop()
            tile.start()

            assertThat(called).hasValue(2)
        }

        @Test
        fun `resourcesStarted will start tile if possible`() {
            val tile = DominoTile(TILE_NAME, factory, createResources = {
                val future = CompletableFuture<Unit>()
                future.complete(Unit)
                future
            })
            tile.start()

            assertThat(tile.state).isEqualTo(DominoTile.State.Started)
        }

        @Test
        fun `resourcesStarted will start tile stopped`() {
            val outerFuture = CompletableFuture<Unit>()
            val tile = DominoTile(TILE_NAME, factory, createResources = { outerFuture })
            tile.start()
            tile.stop()

            outerFuture.complete(Unit)

            assertThat(tile.state).isEqualTo(DominoTile.State.Started)
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

        private var outerConfigUpdateResult: CompletableFuture<Unit>? = null

        private inner class TileConfigurationChangeHandler : ConfigurationChangeHandler<Configuration>(
            service,
            key,
            configFactory,
        ) {
            override fun applyNewConfiguration(
                newConfiguration: Configuration,
                oldConfiguration: Configuration?,
                resources: ResourcesHolder,
            ): CompletableFuture<Unit> {
                val future = CompletableFuture<Unit>()
                calledNewConfigurations.add(newConfiguration to oldConfiguration)
                outerConfigUpdateResult = future
                return future
            }
        }

        private fun tile(): DominoTile {
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
        fun `stop empty last configuration`() {
            val handler = TileConfigurationChangeHandler()
            val tile = DominoTile(TILE_NAME, factory, configurationChangeHandler = handler)
            tile.start()
            handler.lastConfiguration = mock()

            tile.stop()

            assertThat(handler.lastConfiguration).isNull()
        }

        @Test
        fun `close empty last configuration`() {
            val handler = TileConfigurationChangeHandler()
            val tile = DominoTile(TILE_NAME, factory, configurationChangeHandler = handler)
            tile.start()
            handler.lastConfiguration = mock()

            tile.close()

            assertThat(handler.lastConfiguration).isNull()
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
            val config = mock<SmartConfig>()
            val tile = tile()
            tile.start()

            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config))
            outerConfigUpdateResult!!.completeExceptionally(RuntimeException("Bad config"))

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToBadConfig)
            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config))

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToBadConfig)
            assertThat(tile.isRunning).isFalse
        }

        @Test
        fun `onNewConfiguration will apply correct configuration if valid`() {
            val config = mock<SmartConfig> {
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
            val config = mock<SmartConfig> {
                on { getInt(any()) } doReturn 33
            }
            val tile = tile()
            tile.start()

            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config))

            assertThat(tile.state).isEqualTo(DominoTile.State.Created)

            outerConfigUpdateResult!!.complete(Unit)

            assertThat(tile.state).isEqualTo(DominoTile.State.Started)
            assertThat(tile.isRunning).isTrue
        }

        @Test
        fun `onNewConfiguration will call only once if the configuration did not changed`() {
            val config1 = mock<SmartConfig> {
                on { getInt(any()) } doReturn 33
            }
            val config2 = mock<SmartConfig> {
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
            val config1 = mock<SmartConfig> {
                on { getInt(any()) } doReturn 33
            }
            val config2 = mock<SmartConfig> {
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
            val config = mock<SmartConfig> {
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
            val badConfig = mock<SmartConfig> {
                on { getInt(any()) } doReturn 5
            }
            val goodConfig = mock<SmartConfig> {
                on { getInt(any()) } doReturn 17
            }

            val tile = tile()
            tile.start()
            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to badConfig))
            outerConfigUpdateResult!!.completeExceptionally(RuntimeException("Bad config"))
            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToBadConfig)

            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to goodConfig))
            outerConfigUpdateResult!!.complete(Unit)
            assertThat(tile.state).isEqualTo(DominoTile.State.Started)

            assertThat(calledNewConfigurations).contains(Configuration(17) to Configuration(5))
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

        @Test
        fun `applyNewConfiguration handle exceptions`() {
            whenever(configFactory.invoke(any())).thenThrow(RuntimeException("Bad configuration"))
            val config = mock<SmartConfig>()
            whenever(service.registerForUpdates(any())).doAnswer {
                val handler = it.arguments[0] as ConfigurationHandler
                handler.onNewConfiguration(setOf(key), mapOf(key to config))
                registration
            }
            val tile = tile()

            tile.start()

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToBadConfig)
        }

        @Test
        fun `second start will not restart if the configuration had error`() {
            val resourceCreated = AtomicInteger(0)
            whenever(configFactory.invoke(any())).thenThrow(RuntimeException("Bad configuration"))
            val config = mock<SmartConfig>()
            whenever(service.registerForUpdates(any())).doAnswer {
                val handler = it.arguments[0] as ConfigurationHandler
                handler.onNewConfiguration(setOf(key), mapOf(key to config))
                registration
            }
            val tile = DominoTile(
                TILE_NAME,
                factory,
                configurationChangeHandler = TileConfigurationChangeHandler(),
                createResources = {
                    resourceCreated.incrementAndGet()
                    CompletableFuture()
                }
            )
            tile.start()

            tile.start()

            assertThat(resourceCreated).hasValue(1)
        }
    }

    @Nested
    inner class InternalTileTest {
        private fun tile(children: Collection<DominoTile>): DominoTile = DominoTile(
            TILE_NAME,
            factory,
            children = children
        )

        @Nested
        inner class StartTileTests {
            @Test
            fun `startTile will register to follow all the tiles on the first run`() {
                val children = arrayOf<Pair<StubDominoTile, RegistrationHandle>>(
                    StubDominoTile(LifecycleCoordinatorName("component", "1")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "2")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "3")) to mock(),
                )
                val tile = tile(children.map { it.first.dominoTile })

                tile.start()

                children.forEach {
                    verify(coordinator).followStatusChangesByName(setOf(it.first.dominoTile.name))
                }
            }

            @Test
            fun `startTile will not register to follow any tile for the second time`() {
                val children = arrayOf<Pair<StubDominoTile, RegistrationHandle>>(
                    StubDominoTile(LifecycleCoordinatorName("component", "1")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "2")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "3")) to mock(),
                )
                val tile = tile(children.map { it.first.dominoTile })

                tile.start()
                tile.start()

                children.forEach {
                    verify(coordinator).followStatusChangesByName(setOf(it.first.dominoTile.name))
                }
            }

            @Test
            fun `startTile will start all the children`() {
                val children = arrayOf<Pair<StubDominoTile, RegistrationHandle>>(
                    StubDominoTile(LifecycleCoordinatorName("component", "1")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "2")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "3")) to mock(),
                )
                registerChildren(coordinator, children)
                val tile = tile(children.map { it.first.dominoTile })

                tile.start()

                children.forEach {
                    verify(it.first.dominoTile, atLeast(1)).start()
                }
            }
        }

        @Nested
        inner class StartDependenciesIfNeededTests {
            @Test
            fun `parent will start stopped children if errored child recovers`() {
                val children = arrayOf<Pair<StubDominoTile, RegistrationHandle>>(
                    StubDominoTile(LifecycleCoordinatorName("component", "1")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "2")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "3")) to mock(),
                )
                registerChildren(coordinator, children)

                val tile = tile(children.map { it.first.dominoTile })
                tile.start()

                // simulate children starting
                children[0].first.setState(DominoTile.State.Started)
                handler.lastValue.processEvent(CustomEvent(children[0].second,
                    DominoTile.StatusChangeEvent(DominoTile.State.Started)), coordinator)
                children[1].first.setState(DominoTile.State.Started)
                handler.lastValue.processEvent(CustomEvent(children[1].second,
                    DominoTile.StatusChangeEvent(DominoTile.State.Started)), coordinator)
                children[2].first.setState(DominoTile.State.Started)
                handler.lastValue.processEvent(CustomEvent(children[2].second,
                    DominoTile.StatusChangeEvent(DominoTile.State.Started)), coordinator)
                // simulate child failing
                children[2].first.setState(DominoTile.State.StoppedDueToError)
                handler.lastValue.processEvent(CustomEvent(children[2].second,
                    DominoTile.StatusChangeEvent(DominoTile.State.StoppedDueToError)), coordinator)

                // verify other children stopped
                verify(children[0].first.dominoTile).stop()
                verify(children[1].first.dominoTile).stop()
                children[0].first.setState(DominoTile.State.StoppedByParent)
                handler.lastValue.processEvent(CustomEvent(children[0].second,
                    DominoTile.StatusChangeEvent(DominoTile.State.StoppedByParent)), coordinator)
                children[1].first.setState(DominoTile.State.StoppedByParent)
                handler.lastValue.processEvent(CustomEvent(children[1].second,
                    DominoTile.StatusChangeEvent(DominoTile.State.StoppedByParent)), coordinator)

                // simulate errored child recovering
                children.forEach { clearInvocations(it.first.dominoTile) }
                children[2].first.setState(DominoTile.State.Started)
                handler.lastValue.processEvent(CustomEvent(children[2].second,
                    DominoTile.StatusChangeEvent(DominoTile.State.Started)), coordinator)


                verify(children[0].first.dominoTile).start()
                verify(children[1].first.dominoTile).start()
            }

            @Test
            fun `parent status will be set to started if all are running`() {
                val children = arrayOf<Pair<StubDominoTile, RegistrationHandle>>(
                    StubDominoTile(LifecycleCoordinatorName("component", "1")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "2")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "3")) to mock(),
                )
                registerChildren(coordinator, children)

                val tile = tile(children.map { it.first.dominoTile })
                tile.start()

                // simulate children starting
                children[0].first.setState(DominoTile.State.Started)
                handler.lastValue.processEvent(CustomEvent(children[0].second,
                    DominoTile.StatusChangeEvent(DominoTile.State.Started)), coordinator)
                children[1].first.setState(DominoTile.State.Started)
                handler.lastValue.processEvent(CustomEvent(children[1].second,
                    DominoTile.StatusChangeEvent(DominoTile.State.Started)), coordinator)
                children[2].first.setState(DominoTile.State.Started)
                handler.lastValue.processEvent(CustomEvent(children[2].second,
                    DominoTile.StatusChangeEvent(DominoTile.State.Started)), coordinator)

                assertThat(tile.isRunning).isTrue
            }

            @Test
            fun `parent will stop a child that recovers from error if there are still errored children`() {
                val children = arrayOf<Pair<StubDominoTile, RegistrationHandle>>(
                    StubDominoTile(LifecycleCoordinatorName("component" , "1")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component" , "2")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component" , "3")) to mock(),
                )
                registerChildren(coordinator, children)

                val tile = tile(children.map { it.first.dominoTile })
                tile.start()

                // simulate children starting
                children[0].first.setState(DominoTile.State.Started)
                handler.lastValue.processEvent(CustomEvent(children[0].second,
                    DominoTile.StatusChangeEvent(DominoTile.State.Started)), coordinator)
                children[1].first.setState(DominoTile.State.Started)
                handler.lastValue.processEvent(CustomEvent(children[1].second,
                    DominoTile.StatusChangeEvent(DominoTile.State.Started)), coordinator)
                children[2].first.setState(DominoTile.State.Started)
                handler.lastValue.processEvent(CustomEvent(children[2].second,
                    DominoTile.StatusChangeEvent(DominoTile.State.Started)), coordinator)
                // simulate two children failing
                children[0].first.setState(DominoTile.State.StoppedDueToError)
                handler.lastValue.processEvent(CustomEvent(children[0].second,
                    DominoTile.StatusChangeEvent(DominoTile.State.StoppedDueToError)), coordinator)
                children[1].first.setState(DominoTile.State.StoppedDueToError)
                handler.lastValue.processEvent(CustomEvent(children[1].second,
                    DominoTile.StatusChangeEvent(DominoTile.State.StoppedDueToError)), coordinator)


                clearInvocations(children[0].first.dominoTile)
                // simulate one child recovering
                children[0].first.setState(DominoTile.State.Started)
                handler.lastValue.processEvent(CustomEvent(children[0].second,
                    DominoTile.StatusChangeEvent(DominoTile.State.Started)), coordinator)

                verify(children[0].first.dominoTile).stop()
            }

            @Test
            fun `parent status will be set to Started if all the children are started`() {
                val children = arrayOf<Pair<StubDominoTile, RegistrationHandle>>(
                    StubDominoTile(LifecycleCoordinatorName("component" , "1")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component" , "2")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component" , "3")) to mock(),
                )
                registerChildren(coordinator, children)

                val tile = tile(children.map { it.first.dominoTile })
                tile.start()

                // simulate children starting
                children[0].first.setState(DominoTile.State.Started)
                handler.lastValue.processEvent(CustomEvent(children[0].second,
                    DominoTile.StatusChangeEvent(DominoTile.State.Started)), coordinator)
                children[1].first.setState(DominoTile.State.Started)
                handler.lastValue.processEvent(CustomEvent(children[1].second,
                    DominoTile.StatusChangeEvent(DominoTile.State.Started)), coordinator)
                children[2].first.setState(DominoTile.State.Started)
                handler.lastValue.processEvent(CustomEvent(children[2].second,
                    DominoTile.StatusChangeEvent(DominoTile.State.Started)), coordinator)

                assertThat(tile.state).isEqualTo(DominoTile.State.Started)
            }

            @Test
            fun `parent status will not be set to Started if some children have not started yet`() {
                val children = arrayOf<Pair<StubDominoTile, RegistrationHandle>>(
                    StubDominoTile(LifecycleCoordinatorName("component" , "1")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component" , "2")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component" , "3")) to mock(),
                )
                registerChildren(coordinator, children)

                val tile = tile(children.map { it.first.dominoTile })
                tile.start()

                // simulate only 2 out of 3 children starting
                children[0].first.setState(DominoTile.State.Started)
                handler.lastValue.processEvent(CustomEvent(children[0].second,
                    DominoTile.StatusChangeEvent(DominoTile.State.Started)), coordinator)
                children[1].first.setState(DominoTile.State.Started)
                handler.lastValue.processEvent(CustomEvent(children[1].second,
                    DominoTile.StatusChangeEvent(DominoTile.State.Started)), coordinator)

                assertThat(tile.state).isEqualTo(DominoTile.State.Created)
                assertThat(tile.isRunning).isFalse()
            }
        }

        @Nested
        inner class HandleEventTests {

            @Test
            fun `other events will not throw an exception`() {
                val children = listOf<DominoTile>(
                    mock(),
                )
                tile(children)
                class Event : LifecycleEvent

                assertDoesNotThrow {
                    handler.lastValue.processEvent(
                        Event(),
                        coordinator
                    )
                }
            }

            @Test
            fun `when a child goes down with error, the parent also stops due to error`() {
                val children = arrayOf<Pair<StubDominoTile, RegistrationHandle>>(
                    StubDominoTile(LifecycleCoordinatorName("component", "1")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "2")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "3")) to mock(),
                )
                registerChildren(coordinator, children)

                val tile = tile(children.map { it.first.dominoTile })
                tile.start()

                children[0].first.setState(DominoTile.State.StoppedDueToError)
                handler.lastValue.processEvent(CustomEvent(children[0].second,
                    DominoTile.StatusChangeEvent(DominoTile.State.StoppedDueToError)), coordinator)

                assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
            }
        }

        @Nested
        inner class StopTileTests {
            @Test
            fun `stopTile will stop all the children`() {
                val children = arrayOf<Pair<StubDominoTile, RegistrationHandle>>(
                    StubDominoTile(LifecycleCoordinatorName("component", "1")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "2")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "3")) to mock(),
                )
                registerChildren(coordinator, children)

                val tile = tile(children.map { it.first.dominoTile })
                tile.start()

                tile.stop()
                verify(children[0].first.dominoTile).stop()
                verify(children[1].first.dominoTile).stop()
                verify(children[2].first.dominoTile).stop()
            }
        }

        @Nested
        inner class CloseTests {
            @Test
            fun `close will close the registrations`() {
                val children = arrayOf<Pair<StubDominoTile, RegistrationHandle>>(
                    StubDominoTile(LifecycleCoordinatorName("component", "1")) to mock()
                )
                registerChildren(coordinator, children)

                val tile = tile(children.map { it.first.dominoTile })
                tile.start()

                tile.close()
                verify(children[0].first.dominoTile).close()
            }

            @Test
            fun `close will close the coordinator`() {
                val children = arrayOf<Pair<StubDominoTile, RegistrationHandle>>(
                    StubDominoTile(LifecycleCoordinatorName("component", "1")) to mock()
                )
                registerChildren(coordinator, children)

                val tile = tile(children.map { it.first.dominoTile })
                tile.close()

                verify(coordinator).close()
            }

            @Test
            fun `close will close the children`() {
                val children = arrayOf<Pair<StubDominoTile, RegistrationHandle>>(
                    StubDominoTile(LifecycleCoordinatorName("component", "1")) to mock()
                )
                registerChildren(coordinator, children)

                val tile = tile(children.map { it.first.dominoTile })
                tile.start()

                tile.close()
                verify(children[0].first.dominoTile).close()
            }

            @Test
            fun `close will not fail if closing a child fails`() {
                val children = listOf<DominoTile>(
                    mock {
                        on { close() } doThrow RuntimeException("")
                        on { name } doReturn LifecycleCoordinatorName("component", "1")
                    }
                )
                val registration = mock<RegistrationHandle>()
                whenever(coordinator.followStatusChangesByName(any())).thenReturn(registration)
                val tile = tile(children)

                tile.start()
                tile.close()
            }
        }
    }

    @Nested
    inner class LeafTileWithConfigAndResourcesTests {

        private lateinit var outerConfigUpdateResult: CompletableFuture<Unit>
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
            ): CompletableFuture<Unit> {
                val future = CompletableFuture<Unit>()
                calledNewConfigurations.add(newConfiguration to oldConfiguration)
                outerConfigUpdateResult = future
                return future
            }
        }

        @Test
        fun `onNewConfiguration will start the tile if resources started`() {
            val tile = DominoTile(
                TILE_NAME,
                factory,
                createResources = {
                    val future = CompletableFuture<Unit>()
                    future.complete(Unit)
                    future
                },
                configurationChangeHandler = TileConfigurationChangeHandler()
            )
            tile.start()
            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to mock()))
            outerConfigUpdateResult.complete(Unit)

            assertThat(tile.state).isEqualTo(DominoTile.State.Started)
        }

        @Test
        fun `createResources then onNewConfiguration will not start the tile if configuration was bad`() {
            val resourceFuture = CompletableFuture<Unit>()
            val tile = DominoTile(
                TILE_NAME,
                factory,
                createResources = { resourceFuture },
                configurationChangeHandler = TileConfigurationChangeHandler()
            )
            tile.start()
            resourceFuture.complete(Unit)
            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to mock()))
            outerConfigUpdateResult.completeExceptionally(IOException())

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToBadConfig)
        }

        @Test
        fun `onNewConfiguration then createResources will not start the tile if configuration was bad`() {
            val resourceFuture = CompletableFuture<Unit>()
            val tile = DominoTile(
                TILE_NAME,
                factory,
                createResources = { resourceFuture },
                configurationChangeHandler = TileConfigurationChangeHandler()
            )
            tile.start()
            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to mock()))
            outerConfigUpdateResult.completeExceptionally(IOException())
            resourceFuture.complete(Unit)

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToBadConfig)
        }

        @Test
        fun `createResources then onNewConfiguration will not start the tile if resources fail`() {
            val resourceFuture = CompletableFuture<Unit>()
            val tile = DominoTile(
                TILE_NAME,
                factory,
                createResources = { resourceFuture },
                configurationChangeHandler = TileConfigurationChangeHandler()
            )
            tile.start()
            resourceFuture.completeExceptionally(IOException())
            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to mock()))
            outerConfigUpdateResult.complete(Unit)

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
        }

        @Test
        fun `onNewConfiguration then createResources will not start the tile if resources fail`() {
            val resourceFuture = CompletableFuture<Unit>()
            val tile = DominoTile(
                TILE_NAME,
                factory,
                createResources = { resourceFuture },
                configurationChangeHandler = TileConfigurationChangeHandler()
            )
            tile.start()
            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to mock()))
            outerConfigUpdateResult.complete(Unit)
            resourceFuture.completeExceptionally(IOException())

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
        }

        @Test
        fun `createResources then onNewConfiguration will not start the tile if resources and config fail`() {
            val resourceFuture = CompletableFuture<Unit>()
            val tile = DominoTile(
                TILE_NAME,
                factory,
                createResources = { resourceFuture },
                configurationChangeHandler = TileConfigurationChangeHandler()
            )
            tile.start()
            resourceFuture.completeExceptionally(IOException())
            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to mock()))
            outerConfigUpdateResult.completeExceptionally(IOException())

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToBadConfig)
        }

        @Test
        fun `onNewConfiguration then createResources will not start the tile if resources and config fail`() {
            val resourceFuture = CompletableFuture<Unit>()
            val tile = DominoTile(
                TILE_NAME,
                factory,
                createResources = { resourceFuture },
                configurationChangeHandler = TileConfigurationChangeHandler()
            )
            tile.start()
            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to mock()))
            outerConfigUpdateResult.completeExceptionally(IOException())
            resourceFuture.completeExceptionally(IOException())

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToBadConfig)
        }
    }

    private class StubDominoTile(coordinatorName: LifecycleCoordinatorName) {

        val dominoTile by lazy {
            mock<DominoTile> {
                on { state } doAnswer { currentState }
                on { name } doReturn coordinatorName
                on { isRunning } doAnswer { currentState == DominoTile.State.Started }
            }
        }
        private var currentState: DominoTile.State = DominoTile.State.Created

        fun setState(state: DominoTile.State) {
            currentState = state
        }

    }

    private fun registerChildren(coordinator: LifecycleCoordinator, children: Array<Pair<StubDominoTile, RegistrationHandle>>) {
        whenever(coordinator.followStatusChangesByName(any())).thenAnswer { answer ->
            @Suppress("UNCHECKED_CAST")
            val name = answer.arguments[0] as Set<LifecycleCoordinatorName>
            val registration = children.first { it.first.dominoTile.name == name.single() }.second
            registration
        }
    }
}
