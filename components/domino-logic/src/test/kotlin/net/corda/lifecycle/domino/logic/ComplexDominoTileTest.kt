package net.corda.lifecycle.domino.logic

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class ComplexDominoTileTest {

    companion object {
        private const val TILE_NAME = "MyTestTile"
    }

    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { postEvent(any()) } doAnswer {
            handler.lastValue.processEvent(it.getArgument(0) as LifecycleEvent, mock)
        }
        var currentStatus: LifecycleStatus = LifecycleStatus.DOWN
        on { updateStatus(any(), any()) } doAnswer { currentStatus =  it.getArgument(0) }
        on { status } doAnswer { currentStatus }
    }
    private val factory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }

    @Nested
    inner class SimpleLeafTileTests {

        private fun tile(): ComplexDominoTile {
            return ComplexDominoTile(TILE_NAME, factory, mock())
        }

        @Test
        fun `start will start the coordinator if not started`() {
            val tile = tile()

            tile.start()

            verify(coordinator).start()
        }

        @Test
        fun `start will update the coordinator status to UP`() {
            val tile = tile()

            tile.start()

            verify(coordinator).updateStatus(LifecycleStatus.UP)
        }

        @Test
        fun `start will update the status to UP`() {
            val tile = tile()

            tile.start()

            assertThat(tile.state).isEqualTo(LifecycleStatus.UP)
        }

        @Test
        fun `stop will update the status to stopped`() {
            val tile = tile()
            tile.stop()

            assertThat(tile.state).isEqualTo(LifecycleStatus.DOWN)
        }

        @Test
        fun `stop will update the status the tile is started`() {
            val tile = tile()
            tile.start()

            tile.stop()

            assertThat(tile.state).isEqualTo(LifecycleStatus.DOWN)
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
        fun `processEvent update the coordinator state to ERROR if the event is error`() {
            val tile = tile()
            tile.start()

            handler.lastValue.processEvent(ErrorEvent(Exception("")), coordinator)

            verify(coordinator).updateStatus(LifecycleStatus.ERROR)
        }

        @Test
        fun `processEvent will set as ERROR if the event is error`() {
            val tile = tile()
            tile.start()

            handler.lastValue.processEvent(ErrorEvent(Exception("")), coordinator)

            assertThat(tile.state).isEqualTo(LifecycleStatus.ERROR)
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
            tile.start()
            tile.close()

            assertThat(tile.state).isEqualTo(LifecycleStatus.UP)
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

            assertThat(tile.state).isNotEqualTo(DominoTileState.Started)
        }
    }

    @Nested
    inner class LeafTileWithResourcesTests {

        @Test
        fun `startTile called onStart`() {
            var onStartCalled = 0
            @Suppress("UNUSED_PARAMETER")
            fun onStart() {
                onStartCalled++
            }

            val tile = ComplexDominoTile(TILE_NAME, factory, mock(), ::onStart)
            tile.start()

            assertThat(onStartCalled).isEqualTo(1)
        }

        @Test
        fun `second start will not restart anything`() {
            val called = AtomicInteger(0)
            val tile = ComplexDominoTile(TILE_NAME, factory, mock(), onStart = {
                called.incrementAndGet()
            })
            tile.start()

            tile.start()

            assertThat(called).hasValue(1)
        }

        @Test
        fun `second start will recreate the resources if it was stopped`() {
            val called = AtomicInteger(0)
            val tile = ComplexDominoTile(TILE_NAME, factory, mock(), onStart = {
                called.incrementAndGet()
            })

            tile.start()
            tile.stop()
            tile.start()

            assertThat(called).hasValue(2)
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

        private fun tile(): ComplexDominoTile {
            return ComplexDominoTile(TILE_NAME, factory, mock(), configurationChangeHandler = TileConfigurationChangeHandler())
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
            val tile = ComplexDominoTile(TILE_NAME, factory, mock(), configurationChangeHandler = handler)
            tile.start()
            handler.lastConfiguration = mock()

            tile.stop()

            assertThat(handler.lastConfiguration).isNull()
        }

        @Test
        fun `close empty last configuration`() {
            val handler = TileConfigurationChangeHandler()
            val tile = ComplexDominoTile(TILE_NAME, factory, mock(), configurationChangeHandler = handler)
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

            assertThat(tile.state).isEqualTo(LifecycleStatus.DOWN)
            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to config))

            assertThat(tile.state).isEqualTo(LifecycleStatus.DOWN)
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

            assertThat(tile.state).isEqualTo(LifecycleStatus.DOWN)

            outerConfigUpdateResult!!.complete(Unit)

            assertThat(tile.state).isEqualTo(LifecycleStatus.UP)
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
            assertThat(tile.state).isEqualTo(LifecycleStatus.DOWN)

            configurationHandler.firstValue.onNewConfiguration(setOf(key), mapOf(key to goodConfig))
            outerConfigUpdateResult!!.complete(Unit)
            assertThat(tile.state).isEqualTo(LifecycleStatus.UP)

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

            assertThat(tile.state).isEqualTo(LifecycleStatus.DOWN)
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
            val tile = ComplexDominoTile(
                TILE_NAME,
                factory,
                mock(),
                configurationChangeHandler = TileConfigurationChangeHandler(),
                onStart = {
                    resourceCreated.incrementAndGet()
                }
            )
            tile.start()

            tile.start()

            assertThat(resourceCreated).hasValue(1)
        }
    }

    @Nested
    inner class InternalTileTest {
        private fun tile(dependentChildren: Collection<LifecycleCoordinatorName>,
                         managedChildren: Collection<ComplexDominoTile>): ComplexDominoTile =
            ComplexDominoTile(
                TILE_NAME,
                factory,
                mock(),
                dependentChildren = dependentChildren,
                managedChildren = managedChildren
            )

        @Nested
        inner class StartTileTests {

            @Test
            fun `startTile will register to follow all the dependent tiles on the first run`() {
                val children = arrayOf<Pair<StubDominoTile, RegistrationHandle>>(
                    StubDominoTile(LifecycleCoordinatorName("component", "1")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "2")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "3")) to mock(),
                )
                val tile = tile(children.map { it.first.dominoTile.coordinatorName }, emptyList())

                tile.start()

                children.forEach {
                    verify(coordinator).followStatusChangesByName(setOf(it.first.dominoTile.coordinatorName))
                }
            }

            @Test
            fun `startTile will not register to follow any tile for the second time`() {
                val children = arrayOf<Pair<StubDominoTile, RegistrationHandle>>(
                    StubDominoTile(LifecycleCoordinatorName("component", "1")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "2")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "3")) to mock(),
                )
                val tile = tile(children.map { it.first.dominoTile.coordinatorName }, emptyList())

                tile.start()
                tile.start()

                children.forEach {
                    verify(coordinator).followStatusChangesByName(setOf(it.first.dominoTile.coordinatorName))
                }
            }

            @Test
            fun `startTile will start all the managed children`() {
                val children = arrayOf<Pair<StubDominoTile, RegistrationHandle>>(
                    StubDominoTile(LifecycleCoordinatorName("component", "1")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "2")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "3")) to mock(),
                )
                registerChildren(coordinator, children)
                val tile = tile(emptyList(), children.map { it.first.dominoTile })

                tile.start()

                children.forEach {
                    verify(it.first.dominoTile, atLeast(1)).start()
                }
            }
        }

        @Nested
        inner class StartDependenciesIfNeededTests {
            @Test
            fun `parent status will be set to started if all are running`() {
                val children = arrayOf<Pair<StubDominoTile, RegistrationHandle>>(
                    StubDominoTile(LifecycleCoordinatorName("component", "1")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "2")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "3")) to mock(),
                )
                registerChildren(coordinator, children)

                val tile = tile(children.map { it.first.dominoTile.coordinatorName }, emptyList())
                tile.start()

                // simulate children starting
                children[0].first.setState(LifecycleStatus.UP)
                handler.lastValue.processEvent(RegistrationStatusChangeEvent(children[0].second, LifecycleStatus.UP), coordinator)
                children[1].first.setState(LifecycleStatus.UP)
                handler.lastValue.processEvent(RegistrationStatusChangeEvent(children[1].second, LifecycleStatus.UP), coordinator)
                children[2].first.setState(LifecycleStatus.UP)
                handler.lastValue.processEvent(RegistrationStatusChangeEvent(children[2].second, LifecycleStatus.UP), coordinator)
                assertThat(tile.isRunning).isTrue
                assertThat(tile.state).isEqualTo(LifecycleStatus.UP)
            }

            @Test
            fun `parent status will not be set to Started if some children have not started yet`() {
                val children = arrayOf<Pair<StubDominoTile, RegistrationHandle>>(
                    StubDominoTile(LifecycleCoordinatorName("component" , "1")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component" , "2")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component" , "3")) to mock(),
                )
                registerChildren(coordinator, children)

                val tile = tile(children.map { it.first.dominoTile.coordinatorName }, emptyList())
                tile.start()

                // simulate only 2 out of 3 children starting
                children[0].first.setState(LifecycleStatus.UP)
                handler.lastValue.processEvent(RegistrationStatusChangeEvent(children[0].second, LifecycleStatus.UP), coordinator)
                children[1].first.setState(LifecycleStatus.UP)
                handler.lastValue.processEvent(RegistrationStatusChangeEvent(children[1].second, LifecycleStatus.UP), coordinator)

                assertThat(tile.state).isEqualTo(LifecycleStatus.DOWN)
                assertThat(tile.isRunning).isFalse
            }

            @Test
            fun `parent will stop if one of the children was stopped by a different parent component`() {
                val children = arrayOf<Pair<StubDominoTile, RegistrationHandle>>(
                    StubDominoTile(LifecycleCoordinatorName("component" , "1")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component" , "2")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component" , "3")) to mock(),
                )
                registerChildren(coordinator, children)

                val tile = tile(children.map { it.first.dominoTile.coordinatorName }, emptyList())
                tile.start()

                // simulate children starting
                children[0].first.setState(LifecycleStatus.UP)
                handler.lastValue.processEvent(RegistrationStatusChangeEvent(children[0].second, LifecycleStatus.UP), coordinator)
                children[1].first.setState(LifecycleStatus.UP)
                handler.lastValue.processEvent(RegistrationStatusChangeEvent(children[1].second, LifecycleStatus.UP), coordinator)
                children[2].first.setState(LifecycleStatus.UP)
                handler.lastValue.processEvent(RegistrationStatusChangeEvent(children[2].second, LifecycleStatus.UP), coordinator)

                //Simulate child stopping
                children[0].first.setState(LifecycleStatus.DOWN)
                handler.lastValue.processEvent(RegistrationStatusChangeEvent(children[0].second, LifecycleStatus.DOWN), coordinator)

                assertThat(tile.state).isEqualTo(LifecycleStatus.DOWN)
            }
        }

        @Nested
        inner class HandleEventTests {

            @Test
            fun `other events will not throw an exception`() {
                val children = listOf<ComplexDominoTile>(
                    mock(),
                )
                tile(children.map { it.coordinatorName }, emptyList())
                class Event : LifecycleEvent

                assertDoesNotThrow {
                    handler.lastValue.processEvent(
                        Event(),
                        coordinator
                    )
                }
            }
        }

        @Nested
        inner class StopTileTests {
            @Test
            fun `stopTile will stop all the managed children`() {
                val children = arrayOf<Pair<StubDominoTile, RegistrationHandle>>(
                    StubDominoTile(LifecycleCoordinatorName("component", "1")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "2")) to mock(),
                    StubDominoTile(LifecycleCoordinatorName("component", "3")) to mock(),
                )
                registerChildren(coordinator, children)

                val tile = tile(emptyList(), children.map { it.first.dominoTile })
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

                val tile = tile(children.map { it.first.dominoTile.coordinatorName }, emptyList())
                tile.start()

                tile.close()
                verify(children[0].second).close()
            }

            @Test
            fun `close will close the coordinator`() {
                val children = arrayOf<Pair<StubDominoTile, RegistrationHandle>>(
                    StubDominoTile(LifecycleCoordinatorName("component", "1")) to mock()
                )
                registerChildren(coordinator, children)

                val tile = tile(children.map { it.first.dominoTile.coordinatorName }, emptyList())
                tile.close()

                verify(coordinator).close()
            }

            @Test
            fun `close will close the managed children`() {
                val children = arrayOf<Pair<StubDominoTile, RegistrationHandle>>(
                    StubDominoTile(LifecycleCoordinatorName("component", "1")) to mock()
                )
                registerChildren(coordinator, children)

                val tile = tile(emptyList(), children.map { it.first.dominoTile })
                tile.start()

                tile.close()
                verify(children[0].first.dominoTile).close()
            }

            @Test
            fun `close will not fail if closing a child fails`() {
                val children = listOf<ComplexDominoTile>(
                    mock {
                        on { close() } doThrow RuntimeException("")
                        on { coordinatorName } doReturn LifecycleCoordinatorName("component", "1")
                    }
                )
                val registration = mock<RegistrationHandle>()
                whenever(coordinator.followStatusChangesByName(any())).thenReturn(registration)
                val tile = tile(emptyList(), children)

                tile.start()
                tile.close()
            }
        }
    }

    private class StubDominoTile(coordinatorName: LifecycleCoordinatorName) {

        val dominoTile by lazy {
            mock<ComplexDominoTile> {
                on { state } doAnswer { currentState }
                on { this.coordinatorName } doReturn coordinatorName
                on { isRunning } doAnswer { currentState == LifecycleStatus.UP }
            }
        }
        private var currentState: LifecycleStatus = LifecycleStatus.DOWN

        fun setState(state: LifecycleStatus) {
            currentState = state
        }

    }

    private fun registerChildren(coordinator: LifecycleCoordinator, children: Array<Pair<StubDominoTile, RegistrationHandle>>) {
        whenever(coordinator.followStatusChangesByName(any())).thenAnswer { answer ->
            @Suppress("UNCHECKED_CAST")
            val name = answer.arguments[0] as Set<LifecycleCoordinatorName>
            val registration = children.first { it.first.dominoTile.coordinatorName == name.single() }.second
            registration
        }
    }
}