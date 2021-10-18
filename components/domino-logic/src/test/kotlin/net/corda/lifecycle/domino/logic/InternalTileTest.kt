package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
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
import java.util.concurrent.atomic.AtomicBoolean

class InternalTileTest {
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { postEvent(any()) } doAnswer {
            handler.lastValue.processEvent(it.getArgument(0) as LifecycleEvent, mock)
        }
    }
    private val factory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private inner class Tile(
        override val children: Collection<DominoTile>,
    ) : DominoTile(factory)
    private inner class TileWithResources(
        override val children: Collection<DominoTile>,
    ) : DominoTile(factory) {
        var resourceCreated = false
        override fun createResources() {
            resourceCreated = true
        }

        fun createResource(resource: AutoCloseable) {
            resources.keep(resource)
        }
    }

    @Nested
    inner class StartTileTests {
        @Test
        fun `startTile will register to follow all the tiles on the first run`() {
            val names = (1..3).map {
                LifecycleCoordinatorName("component-$it")
            }
            val children = names.map { theName ->
                mock<DominoTile> {
                    on { name } doReturn theName
                }
            }
            val tile = Tile(children)

            tile.start()

            verify(coordinator).followStatusChangesByName(setOf(names[1]))
        }

        @Test
        fun `startTile will not register to follow any tile for the second time`() {
            val names = (1..3).map {
                LifecycleCoordinatorName("component-$it")
            }
            val children = names.map { theName ->
                mock<DominoTile> {
                    on { name } doReturn theName
                }
            }
            val tile = Tile(children)

            tile.start()
            tile.start()

            verify(coordinator, times(1)).followStatusChangesByName(setOf(names[1]))
        }

        @Test
        fun `startTile will start all the children`() {
            val children = listOf<DominoTile>(mock(), mock(), mock())
            val tile = Tile(children)

            tile.start()

            children.forEach {
                verify(it, atLeast(1)).start()
            }
        }
    }

    @Nested
    inner class StartKidsIfNeededTests {
        @Test
        fun `startKidsIfNeeded will start all if there is no error child`() {
            val children = listOf<DominoTile>(
                mock {
                    on { state } doReturn DominoTile.State.StoppedByParent
                },
                mock {
                    on { state } doReturn DominoTile.State.Started
                },
                mock {
                    on { state } doReturn DominoTile.State.Created
                },
            )

            Tile(children)
            handler.lastValue.processEvent(
                RegistrationStatusChangeEvent(
                    mock(),
                    LifecycleStatus.UP
                ),
                coordinator
            )

            verify(children[0]).start()
            verify(children[1]).start()
            verify(children[2]).start()
        }

        @Test
        fun `startKidsIfNeeded will set status to up if all are running`() {
            val children = listOf<DominoTile>(
                mock {
                    on { state } doReturn DominoTile.State.Started
                    on { isRunning } doReturn true
                },
                mock {
                    on { state } doReturn DominoTile.State.Started
                    on { isRunning } doReturn true
                },
                mock {
                    on { state } doReturn DominoTile.State.Started
                    on { isRunning } doReturn true
                },
            )

            val tile = Tile(children)
            handler.lastValue.processEvent(
                RegistrationStatusChangeEvent(
                    mock(),
                    LifecycleStatus.UP
                ),
                coordinator
            )

            assertThat(tile.isRunning).isTrue
        }

        @Test
        fun `startKidsIfNeeded will not start any if there is error child`() {
            val children = listOf<DominoTile>(
                mock {
                    on { state } doReturn DominoTile.State.StoppedByParent
                },
                mock {
                    on { state } doReturn DominoTile.State.Started
                },
                mock {
                    on { state } doReturn DominoTile.State.StoppedDueToError
                },
                mock {
                    on { state } doReturn DominoTile.State.Created
                },
            )

            Tile(children)
            handler.lastValue.processEvent(
                RegistrationStatusChangeEvent(
                    mock(),
                    LifecycleStatus.UP
                ),
                coordinator
            )

            children.forEach {
                verify(it, never()).start()
            }
        }

        @Test
        fun `startKidsIfNeeded will stop all non error children`() {
            val children = listOf<DominoTile>(
                mock {
                    on { state } doReturn DominoTile.State.StoppedByParent
                },
                mock {
                    on { state } doReturn DominoTile.State.Started
                },
                mock {
                    on { state } doReturn DominoTile.State.StoppedDueToError
                },
                mock {
                    on { state } doReturn DominoTile.State.Created
                },
            )

            Tile(children)
            handler.lastValue.processEvent(
                RegistrationStatusChangeEvent(
                    mock(),
                    LifecycleStatus.UP
                ),
                coordinator
            )

            verify(children[0]).stop()
            verify(children[1]).stop()
            verify(children[3]).stop()
        }

        @Test
        fun `Tests for startKidsIfNeeded`() {
            val children = listOf<DominoTile>(
                mock {
                    on { state } doReturn DominoTile.State.StoppedByParent
                },
                mock {
                    on { state } doReturn DominoTile.State.StoppedDueToError
                },
                mock {
                    on { state } doReturn DominoTile.State.Created
                },
            )

            Tile(children)
            handler.lastValue.processEvent(
                RegistrationStatusChangeEvent(
                    mock(),
                    LifecycleStatus.UP
                ),
                coordinator
            )

            verify(children[1], never()).start()
        }

        @Test
        fun `startKidsIfNeeded will set the status to UP if all the children are running`() {
            val children = listOf<DominoTile>(
                mock {
                    on { isRunning } doReturn true
                },
                mock {
                    on { isRunning } doReturn true
                },
                mock {
                    on { isRunning } doReturn true
                },
            )

            val tile = Tile(children)
            handler.lastValue.processEvent(
                RegistrationStatusChangeEvent(
                    mock(),
                    LifecycleStatus.UP
                ),
                coordinator
            )

            assertThat(tile.isRunning).isTrue
        }
        @Test
        fun `startKidsIfNeeded will not set the status to UP if a child is not running`() {
            val children = listOf<DominoTile>(
                mock {
                    on { isRunning } doReturn true
                },
                mock {
                    on { isRunning } doReturn false
                },
                mock {
                    on { isRunning } doReturn true
                },
            )

            val tile = Tile(children)
            handler.lastValue.processEvent(
                RegistrationStatusChangeEvent(
                    mock(),
                    LifecycleStatus.UP
                ),
                coordinator
            )

            assertThat(tile.isRunning).isFalse
        }
    }

    @Nested
    inner class HandleEventTests {
        @Test
        fun `up will start the tile`() {
            val children = listOf<DominoTile>(
                mock {
                    on { isRunning } doReturn true
                },
                mock {
                    on { isRunning } doReturn true
                },
                mock {
                    on { isRunning } doReturn true
                },
            )
            Tile(children)

            handler.lastValue.processEvent(
                RegistrationStatusChangeEvent(
                    mock(),
                    LifecycleStatus.UP
                ),
                coordinator
            )

            verify(children.first()).start()
        }

        @Test
        fun `other events will not throw an exception`() {
            val children = listOf<DominoTile>(
                mock(),
            )
            Tile(children)
            class Event : LifecycleEvent

            assertDoesNotThrow {
                handler.lastValue.processEvent(
                    Event(),
                    coordinator
                )
            }
        }

        @Test
        fun `down without error will stop the tile`() {
            val children = listOf(
                mock<DominoTile> {
                    on { state } doReturn DominoTile.State.StoppedByParent
                }
            )

            Tile(children)
            handler.lastValue.processEvent(
                RegistrationStatusChangeEvent(
                    mock(),
                    LifecycleStatus.DOWN
                ),
                coordinator
            )

            verify(children.first()).stop()
        }

        @Test
        fun `down with error will post error`() {
            val children = listOf(
                mock {
                    on { state } doReturn DominoTile.State.StoppedDueToError
                },
                mock<DominoTile> {
                    on { state } doReturn DominoTile.State.StoppedByParent
                }
            )

            val tile = Tile(children)
            handler.lastValue.processEvent(
                RegistrationStatusChangeEvent(
                    mock(),
                    LifecycleStatus.ERROR
                ),
                coordinator
            )

            assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
        }
    }

    @Nested
    inner class StopTileTests {
        @Test
        fun `stopTile will stop all the non error children`() {
            val children = listOf<DominoTile>(
                mock {
                    on { state } doReturn DominoTile.State.Started
                },
                mock {
                    on { state } doReturn DominoTile.State.StoppedDueToError
                },
                mock {
                    on { state } doReturn DominoTile.State.Created
                }
            )
            val tile = Tile(children)

            tile.stop()

            verify(children[0]).stop()
            verify(children[2]).stop()
        }

        @Test
        fun `stopTile will not stop error children`() {
            val children = listOf<DominoTile>(
                mock {
                    on { state } doReturn DominoTile.State.Started
                },
                mock {
                    on { state } doReturn DominoTile.State.StoppedDueToError
                },
                mock {
                    on { state } doReturn DominoTile.State.Created
                }
            )
            val tile = Tile(children)

            tile.stop()

            verify(children[1], never()).stop()
        }
    }

    @Nested
    inner class CloseTests {
        @Test
        fun `close will close the registrations`() {
            val children = listOf<DominoTile>(mock())
            val registration = mock<RegistrationHandle>()
            doReturn(registration).whenever(coordinator).followStatusChangesByName(any())
            val tile = Tile(children)
            tile.start()

            tile.close()

            verify(registration).close()
        }

        @Test
        fun `close will not try to close registration if non where open`() {
            val children = listOf<DominoTile>(mock())
            val registration = mock<RegistrationHandle>()
            doReturn(registration).whenever(coordinator).followStatusChangesByName(any())
            val tile = Tile(children)

            tile.close()

            verify(registration, never()).close()
        }

        @Test
        fun `close will close the coordinator`() {
            val children = listOf<DominoTile>(mock())
            val tile = Tile(children)

            tile.close()

            verify(coordinator).close()
        }

        @Test
        fun `close will close the children`() {
            val children = listOf<DominoTile>(mock())
            val tile = Tile(children)

            tile.close()

            verify(children.first()).close()
        }

        @Test
        fun `close will not fail if closing a child fails`() {
            val children = listOf<DominoTile>(
                mock {
                    on { close() } doThrow RuntimeException("")
                }
            )
            val tile = Tile(children)

            tile.close()
        }
    }
    @Nested
    inner class WithResourcesTest {
        @Test
        fun `start will create the resources`() {
            val children = listOf<DominoTile>(
                mock {
                    on { state } doReturn DominoTile.State.Started
                    on { isRunning } doReturn true
                },
                mock {
                    on { state } doReturn DominoTile.State.Started
                    on { isRunning } doReturn true
                },
                mock {
                    on { state } doReturn DominoTile.State.Started
                    on { isRunning } doReturn true
                },
            )
            val tile = TileWithResources(children)

            tile.start()

            assertThat(tile.resourceCreated).isTrue
        }

        @Test
        fun `stop will close the resources`() {
            val children = listOf<DominoTile>(
                mock {
                    on { state } doReturn DominoTile.State.Started
                    on { isRunning } doReturn true
                },
                mock {
                    on { state } doReturn DominoTile.State.Started
                    on { isRunning } doReturn true
                },
                mock {
                    on { state } doReturn DominoTile.State.Started
                    on { isRunning } doReturn true
                },
            )
            val tile = TileWithResources(children)
            val closed = AtomicBoolean(false)
            tile.start()
            tile.createResource {
                closed.set(true)
            }

            tile.stop()

            assertThat(closed).isTrue
        }
    }
}
