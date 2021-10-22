package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.io.IOException

class DominoTileTest {

//    private val handler = argumentCaptor<LifecycleEventHandler>()
//    private val coordinator = mock<LifecycleCoordinator> {
//        on { postEvent(any()) } doAnswer {
//            handler.lastValue.processEvent(it.getArgument(0) as LifecycleEvent, mock)
//        }
//    }
//    private val factory = mock<LifecycleCoordinatorFactory> {
//        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
//    }
//
//    @Nested
//    inner class SimpleLeafTileTests {
//
//        inner class Tile : DominoTile(factory)
//
//        @Test
//        fun `start will update the status to started`() {
//            val tile = Tile()
//
//            tile.start()
//
//            assertThat(tile.state).isEqualTo(DominoTile.State.Started)
//        }
//
//        @Test
//        fun `start will start the coordinator if not started`() {
//            val tile = Tile()
//
//            tile.start()
//
//            verify(coordinator).start()
//        }
//
//
//        @Test
//        fun `stop will update the status to stopped`() {
//            val tile = Tile()
//            tile.stop()
//
//            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedByParent)
//        }
//
//        @Test
//        fun `stop will update the status the tile is started`() {
//            val tile = Tile()
//            tile.start()
//
//            tile.stop()
//
//            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedByParent)
//        }
//
//        @Test
//        fun `isRunning will return true if state is started`() {
//            val tile = Tile()
//            tile.start()
//
//            assertThat(tile.isRunning).isTrue
//        }
//
//        @Test
//        fun `isRunning will return false if state is not started`() {
//            val tile = Tile()
//            tile.gotError(Exception(""))
//
//            assertThat(tile.isRunning).isFalse
//        }
//
//        @Test
//        fun `start will not update the coordinator state from error to up`() {
//            val tile = Tile()
//            tile.gotError(Exception(""))
//
//            tile.start()
//
//            verify(coordinator).updateStatus(LifecycleStatus.ERROR)
//        }
//
//        @Test
//        fun `sendError will update the coordinator state from up to error`() {
//            val tile = Tile()
//            tile.start()
//
//            tile.gotError(Exception(""))
//
//            verify(coordinator).updateStatus(LifecycleStatus.ERROR)
//        }
//
//        @Test
//        fun `stop will update the coordinator state from up to down`() {
//            val tile = Tile()
//            tile.start()
//
//            tile.stop()
//
//            verify(coordinator).updateStatus(LifecycleStatus.DOWN)
//        }
//
//        @Test
//        fun `processEvent will set as error if the event is error`() {
//            val tile = Tile()
//            tile.start()
//
//            handler.lastValue.processEvent(ErrorEvent(Exception("")), coordinator)
//
//            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
//        }

        //TODO: We can't verify this anymore (what we should instead verify that children/external components don't start/stop
        //When this happens
//    @Test
//    fun `processEvent will not start the tile the second time`() {
//        val tile = Tile()
//        tile.start()
//
//        handler.lastValue.processEvent(StartEvent(), coordinator)
//
//        assertThat(tile.started).isEqualTo(0)
//    }
//
//        @Test
//        fun `handleEvent can handle unknown event`() {
//            val tile = Tile()
//            val event = RegistrationStatusChangeEvent(
//                mock(),
//                LifecycleStatus.UP
//            )
//            tile.start()
//
//            assertDoesNotThrow {
//                handler.lastValue.processEvent(event, coordinator)
//            }
//        }
//
//        @Test
//        fun `processEvent will ignore the stop event`() {
//            Tile()
//
//            handler.lastValue.processEvent(StopEvent(), coordinator)
//        }
//
//        @Test
//        fun `processEvent will ignore unexpected event`() {
//            Tile()
//
//            handler.lastValue.processEvent(
//                object : LifecycleEvent {},
//                coordinator
//            )
//        }
//
//        @Test
//        fun `onError will set state to error`() {
//            val tile = Tile()
//            tile.start()
//
//            tile.gotError(IOException(""))
//
//            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
//        }
//
//        @Test
//        fun `close will not change the tiles state`() {
//            val tile = Tile()
//
//            tile.close()
//
//            assertThat(tile.state).isEqualTo(DominoTile.State.Created)
//        }
//
//        @Test
//        fun `close will close the coordinator`() {
//            val tile = Tile()
//
//            tile.close()
//
//            verify(coordinator).close()
//        }
//
//        @Test
//        fun `withLifecycleLock return the invocation value`() {
//            val tile = Tile()
//
//            val data = tile.withLifecycleLock {
//                33
//            }
//
//            assertThat(data).isEqualTo(33)
//        }
//    }
//
//    @Nested
//    inner class LeafTileWithResourcesTests {
//
//        @Test
//        fun `startTile called createResource`() {
//            var createResourceCalled = 0
//            val tile = object : DominoTile(factory) {
//                override fun createResources(resources: ResourcesHolder) {
//                    createResourceCalled ++
//                }
//            }
//
//            tile.start()
//
//            assertThat(createResourceCalled).isEqualTo(1)
//        }
//
//        @Test
//        fun `startTile will set error if created resource failed`() {
//            val tile = object : DominoTile(factory) {
//                override fun createResources(resources: ResourcesHolder) {
//                    throw IOException("")
//                }
//            }
//
//            tile.start()
//
//            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
//        }
//
//        @Test
//        fun `stopTile will close all the resources`() {
//            val actions = mutableListOf<Int>()
//            val tile = object : DominoTile(factory) {
//                override fun createResources(resources: ResourcesHolder) {
//                    resources.keep {
//                        actions.add(1)
//                    }
//                    resources.keep {
//                        actions.add(2)
//                    }
//                    resources.keep {
//                        actions.add(3)
//                    }
//                }
//            }
//
//            tile.start()
//
//            tile.stop()
//
//            assertThat(actions).isEqualTo(listOf(3, 2, 1))
//        }
//
//        @Test
//        fun `stopTile will ignore error during stop`() {
//            val actions = mutableListOf<Int>()
//            val tile = object : DominoTile(factory) {
//                override fun createResources(resources: ResourcesHolder) {
//                    resources.keep {
//                        actions.add(1)
//                    }
//                    resources.keep {
//                        throw IOException("")
//                    }
//                    resources.keep {
//                        actions.add(3)
//                    }
//                }
//            }
//            tile.start()
//
//            tile.stop()
//
//            assertThat(actions).isEqualTo(listOf(3, 1))
//        }
//
//        @Test
//        fun `stopTile will not run the same actions again`() {
//            val actions = mutableListOf<Int>()
//            val tile = object : DominoTile(factory) {
//                override fun createResources(resources: ResourcesHolder) {
//                    resources.keep {
//                        actions.add(1)
//                    }
//                }
//            }
//
//            tile.start()
//            tile.stop()
//            tile.stop()
//            tile.stop()
//
//            assertThat(actions).isEqualTo(listOf(1))
//        }
//    }
//
//    @Nested
//    inner class TileWithChildrenTests {
//
//    }

}
