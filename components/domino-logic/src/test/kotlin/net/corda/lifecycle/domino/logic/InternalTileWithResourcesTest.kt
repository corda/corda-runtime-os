package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.StartEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.concurrent.atomic.AtomicBoolean

class InternalTileWithResourcesTest {
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { start() } doAnswer {
            handler.lastValue.processEvent(StartEvent(), mock)
        }
    }
    private val factory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private inner class Tile(
        override val children: Collection<DominoTile>,
    ) : InternalTileWithResources(factory) {
        var resourceCreated = false
        override fun createResources() {
            resourceCreated = true
        }

        fun createResource(resource: AutoCloseable) {
            resources.keep(resource)
        }
    }

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
        val tile = Tile(children)

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
        val tile = Tile(children)
        val closed = AtomicBoolean(false)
        tile.start()
        tile.createResource {
            closed.set(true)
        }

        tile.stop()

        assertThat(closed).isTrue
    }
}
