package net.corda.p2p.gateway.domino

import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.io.IOException

class DominoTileTest {
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { start() } doAnswer {
            handler.lastValue.processEvent(StartEvent(), mock)
        }
    }
    private val factory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    inner class Tile : DominoTile(factory) {
        var started = 0
        var stopped = 0
        var childStarted = 0
        var childStopped = 0
        override fun startTile() {
            started ++
        }

        override fun stopTile() {
            stopped ++
        }

        override fun onChildStarted() {
            childStarted ++
            super.onChildStarted()
        }

        override fun onChildStopped() {
            childStopped ++
            super.onChildStopped()
        }

        fun setState(newState: State) {
            updateState(newState)
        }

        fun sendError(e: Exception) {
            gotError(e)
        }
    }

    @Test
    fun `start will start the coordinator if not started`() {
        val tile = Tile()

        tile.start()

        verify(coordinator).start()
    }

    @Test
    fun `start will not start the coordinator if started`() {
        val tile = Tile()
        tile.setState(DominoTile.State.Started)

        tile.start()

        verify(coordinator, never()).start()
    }

    @Test
    fun `start will not start the tile if started`() {
        val tile = Tile()
        tile.setState(DominoTile.State.Started)

        tile.start()

        assertThat(tile.started).isEqualTo(0)
    }

    @Test
    fun `start will not start the coordinator if stopped`() {
        val tile = Tile()
        tile.setState(DominoTile.State.StoppedByParent)

        tile.start()

        verify(coordinator, never()).start()
    }

    @Test
    fun `start start the tile if stopped`() {
        val tile = Tile()
        tile.setState(DominoTile.State.StoppedByParent)

        tile.start()

        assertThat(tile.started).isEqualTo(1)
    }

    @Test
    fun `start will not start the coordinator if errored`() {
        val tile = Tile()
        tile.setState(DominoTile.State.StoppedDueToError)

        tile.start()

        verify(coordinator, never()).start()
    }

    @Test
    fun `start will not start the tile if errored`() {
        val tile = Tile()
        tile.setState(DominoTile.State.StoppedDueToError)

        tile.start()

        assertThat(tile.started).isEqualTo(0)
    }

    @Test
    fun `stop will stop the tile is started`() {
        val tile = Tile()
        tile.setState(DominoTile.State.Started)

        tile.stop()

        assertThat(tile.stopped).isEqualTo(1)
    }

    @Test
    fun `stop will not stop the tile is stopped`() {
        val tile = Tile()
        tile.setState(DominoTile.State.StoppedByParent)

        tile.stop()

        assertThat(tile.stopped).isEqualTo(0)
    }

    @Test
    fun `stop will update the status the tile is started`() {
        val tile = Tile()
        tile.setState(DominoTile.State.Started)

        tile.stop()

        assertThat(tile.state).isEqualTo(DominoTile.State.StoppedByParent)
    }

    @Test
    fun `isRunning will return true if state is started`() {
        val tile = Tile()
        tile.setState(DominoTile.State.Started)

        assertThat(tile.isRunning).isTrue
    }

    @Test
    fun `isRunning will return true if state is not started`() {
        val tile = Tile()
        tile.setState(DominoTile.State.StoppedDueToError)

        assertThat(tile.isRunning).isFalse
    }

    @Test
    fun `updateState will update the coordinator state from down to up`() {
        val tile = Tile()
        tile.setState(DominoTile.State.StoppedDueToError)

        tile.setState(DominoTile.State.Started)

        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `updateState will update the coordinator state from up to down`() {
        val tile = Tile()
        tile.setState(DominoTile.State.Started)

        tile.setState(DominoTile.State.StoppedDueToError)

        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `processEvent will set as error if the event is error`() {
        val tile = Tile()
        tile.setState(DominoTile.State.Started)

        handler.lastValue.processEvent(ErrorEvent(Exception("")), coordinator)

        assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
    }

    @Test
    fun `processEvent will start the tile after start event`() {
        val tile = Tile()

        handler.lastValue.processEvent(StartEvent(), coordinator)

        assertThat(tile.started).isEqualTo(1)
    }

    @Test
    fun `processEvent will not start the tile the second time`() {
        val tile = Tile()
        tile.setState(DominoTile.State.Started)

        handler.lastValue.processEvent(StartEvent(), coordinator)

        assertThat(tile.started).isEqualTo(0)
    }

    @Test
    fun `processEvent will ignore the stop event`() {
        Tile()

        handler.lastValue.processEvent(StopEvent(), coordinator)
    }

    @Test
    fun `processEvent will call child start on RegistrationStatusChangeEvent UP`() {
        val tile = Tile()

        handler.lastValue.processEvent(
            RegistrationStatusChangeEvent(
                mock(),
                LifecycleStatus.UP
            ),
            coordinator
        )

        assertThat(tile.childStarted).isEqualTo(1)
    }

    @Test
    fun `processEvent will call child stopped on RegistrationStatusChangeEvent DOWN`() {
        val tile = Tile()

        handler.lastValue.processEvent(
            RegistrationStatusChangeEvent(
                mock(),
                LifecycleStatus.DOWN
            ),
            coordinator
        )

        assertThat(tile.childStopped).isEqualTo(1)
    }

    @Test
    fun `processEvent will ignore unexpected event`() {
        Tile()

        handler.lastValue.processEvent(
            object : LifecycleEvent {},
            coordinator
        )
    }

    @Test
    fun `onError will set state to error`() {
        val tile = Tile()
        tile.setState(DominoTile.State.Started)

        tile.sendError(IOException(""))

        assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
    }

    @Test
    fun `onError will stop the tile`() {
        val tile = Tile()
        tile.setState(DominoTile.State.Started)

        tile.sendError(IOException(""))

        assertThat(tile.stopped).isEqualTo(1)
    }

    @Test
    fun `onError so nothing if state is errored`() {
        val tile = Tile()
        tile.setState(DominoTile.State.StoppedDueToError)

        tile.sendError(IOException(""))

        assertThat(tile.stopped).isEqualTo(0)
    }

    @Test
    fun `close will stop the tile`() {
        val tile = Tile()

        tile.close()

        assertThat(tile.stopped).isEqualTo(1)
    }

    @Test
    fun `close will close the coordinator`() {
        val tile = Tile()

        tile.close()

        verify(coordinator).close()
    }
}
