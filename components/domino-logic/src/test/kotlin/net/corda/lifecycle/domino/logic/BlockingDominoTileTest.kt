package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.concurrent.CompletableFuture

class BlockingDominoTileTest {

    val startFuture = CompletableFuture<Unit>()
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private var currentStatus: LifecycleStatus = LifecycleStatus.DOWN
    private val coordinator = mock<LifecycleCoordinator> {
        on { start() } doAnswer {
            handler.lastValue.processEvent(StartEvent(), mock)
        }
        on { stop() } doAnswer {
            handler.lastValue.processEvent(StopEvent(), mock)
        }
        on { postEvent(any()) } doAnswer {
            handler.lastValue.processEvent(it.getArgument(0) as LifecycleEvent, mock)
        }
        on { updateStatus(any(), any()) } doAnswer {
            currentStatus = it.getArgument(0) as LifecycleStatus
        }
        on { status } doAnswer { currentStatus }
    }
    private val factory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    val tile = BlockingDominoTile("myTile", factory, startFuture)

    @Test
    fun `the tile status doesn't change on start if the future is not completed`() {
        tile.start()
        verify(coordinator, never()).updateStatus(any(), any())
    }

    @Test
    fun `the tile status goes UP if the future completes after start`() {
        tile.start()
        startFuture.complete(Unit)

        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `the tile status goes UP if the future completes before start`() {
        startFuture.complete(Unit)
        tile.start()

        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `the tile status goes to ERROR if the future is completed exceptionally`() {
        startFuture.completeExceptionally(IllegalStateException())
        tile.start()

        verify(coordinator).updateStatus(eq(LifecycleStatus.ERROR), any())
    }

    @Test
    fun `the tile status goes UP, DOWN if the future completed and the tile stopped`() {
        startFuture.complete(Unit)
        tile.start()
        tile.stop()

        verify(coordinator).updateStatus(LifecycleStatus.UP)
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `the tile status stays ERROR if if the future is completed exceptionally and then start stop`() {
        startFuture.completeExceptionally(IllegalStateException())
        tile.start()
        tile.stop()

        verify(coordinator).updateStatus(eq(LifecycleStatus.ERROR), any())
        verify(coordinator, never()).updateStatus(LifecycleStatus.UP)
        verify(coordinator, never()).updateStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `the tile status goes UP, DOWN, UP if the future is completed`() {
        startFuture.complete(Unit)
        tile.start()
        tile.stop()
        tile.start()

        verify(coordinator, times(2)).updateStatus(LifecycleStatus.UP)
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }
}