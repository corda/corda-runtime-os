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
        on { postEvent(any()) } doAnswer {
            handler.lastValue.processEvent(it.getArgument(0) as LifecycleEvent, mock)
        }
    }
    private val factory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    inner class Tile : DominoTile(factory) {
        var started = 0
        var stopped = 0
        var handledEvent: LifecycleEvent? = null
        override fun startTile() {
            started ++
        }

        override fun stopTile(dueToError: Boolean) {
            stopped ++
        }

        fun sendError(e: Exception) {
            gotError(e)
        }

        fun sendStarted() {
            started()
        }

        override fun handleEvent(event: LifecycleEvent): Boolean {
            handledEvent = event
            return false
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
        tile.sendStarted()

        tile.start()

        verify(coordinator, never()).start()
    }

    @Test
    fun `start will not start the tile if started`() {
        val tile = Tile()
        tile.sendStarted()

        tile.start()

        assertThat(tile.started).isEqualTo(0)
    }

    @Test
    fun `start will not start the coordinator if stopped`() {
        val tile = Tile()
        tile.stop()

        tile.start()

        verify(coordinator, never()).start()
    }

    @Test
    fun `start start the tile if stopped`() {
        val tile = Tile()
        tile.stop()

        tile.start()

        assertThat(tile.started).isEqualTo(1)
    }

    @Test
    fun `start will not start the coordinator if errored`() {
        val tile = Tile()
        tile.sendError(Exception(""))

        tile.start()

        verify(coordinator, never()).start()
    }

    @Test
    fun `start will not start the tile if errored`() {
        val tile = Tile()
        tile.sendError(Exception(""))

        tile.start()

        assertThat(tile.started).isEqualTo(0)
    }

    @Test
    fun `stop will stop the tile is started`() {
        val tile = Tile()
        tile.sendStarted()

        tile.stop()

        assertThat(tile.stopped).isEqualTo(1)
    }

    @Test
    fun `stop will not stop the tile is stopped`() {
        val tile = Tile()
        tile.stop()

        tile.stop()

        assertThat(tile.stopped).isEqualTo(1)
    }

    @Test
    fun `stop will update the status the tile is started`() {
        val tile = Tile()
        tile.sendStarted()

        tile.stop()

        assertThat(tile.state).isEqualTo(DominoTile.State.StoppedByParent)
    }

    @Test
    fun `isRunning will return true if state is started`() {
        val tile = Tile()
        tile.sendStarted()

        assertThat(tile.isRunning).isTrue
    }

    @Test
    fun `isRunning will return false if state is not started`() {
        val tile = Tile()
        tile.sendError(Exception(""))

        assertThat(tile.isRunning).isFalse
    }

    @Test
    fun `updateState will update the coordinator state from down to up`() {
        val tile = Tile()
        tile.sendError(Exception(""))

        tile.sendStarted()

        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `updateState will update the coordinator state from up to error`() {
        val tile = Tile()
        tile.sendStarted()

        tile.sendError(Exception(""))

        verify(coordinator).updateStatus(LifecycleStatus.ERROR)
    }

    @Test
    fun `updateState will update the coordinator state from up to down`() {
        val tile = Tile()
        tile.sendStarted()

        tile.stop()

        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `processEvent will set as error if the event is error`() {
        val tile = Tile()
        tile.sendStarted()

        handler.lastValue.processEvent(ErrorEvent(Exception("")), coordinator)

        assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
    }

    @Test
    fun `processEvent will not start the tile the second time`() {
        val tile = Tile()
        tile.sendStarted()

        handler.lastValue.processEvent(StartEvent(), coordinator)

        assertThat(tile.started).isEqualTo(0)
    }

    @Test
    fun `processEvent will ignore the stop event`() {
        Tile()

        handler.lastValue.processEvent(StopEvent(), coordinator)
    }

    @Test
    fun `processEvent will call implementation for any other event`() {
        val tile = Tile()
        val event = RegistrationStatusChangeEvent(
            mock(),
            LifecycleStatus.UP
        )

        handler.lastValue.processEvent(
            event,
            coordinator
        )

        assertThat(tile.handledEvent).isEqualTo(event)
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
        tile.sendStarted()

        tile.sendError(IOException(""))

        assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
    }

    @Test
    fun `onError will stop the tile`() {
        val tile = Tile()
        tile.sendStarted()

        tile.sendError(IOException(""))

        assertThat(tile.stopped).isEqualTo(1)
    }

    @Test
    fun `onError so nothing if state is errored`() {
        val tile = Tile()
        tile.sendError(Exception(""))

        tile.sendError(IOException(""))

        assertThat(tile.stopped).isEqualTo(1)
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

    @Test
    fun `Tile will not handle any event after it had been closed`() {
        val tile = Tile()
        val event = object : LifecycleEvent {
        }

        tile.close()

        handler.lastValue.processEvent(event, coordinator)

        assertThat(tile.handledEvent).isNull()
    }

    @Test
    fun `withLifecycleLock return the invocation value`() {
        val tile = Tile()

        val data = tile.withLifecycleLock {
            33
        }

        assertThat(data).isEqualTo(33)
    }
}
