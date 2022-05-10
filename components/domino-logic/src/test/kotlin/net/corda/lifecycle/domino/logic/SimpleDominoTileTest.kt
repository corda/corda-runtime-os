package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class SimpleDominoTileTest {
    private val coordinator = mock< LifecycleCoordinator>()
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) } doReturn coordinator
    }

    private val tile = SimpleDominoTile("name", coordinatorFactory)

    @Test
    fun `start starts the coordinator`() {
        tile.start()

        verify(coordinator).start()
    }

    @Test
    fun `stop will change the status to stop by parent`() {
        tile.stop()

        assertThat(tile.state).isEqualTo(DominoTileState.StoppedByParent)
    }

    @Test
    fun `close will close the coordinator`() {
        tile.close()

        verify(coordinator).close()
    }

    @Test
    fun `dependentChildren is empty`() {
        assertThat(tile.dependentChildren).isEmpty()
    }
    @Test
    fun `managedChildren is empty`() {
        assertThat(tile.managedChildren).isEmpty()
    }

    @Test
    fun `updateState change the state`() {
        tile.updateState(DominoTileState.Started)

        assertThat(tile.state).isEqualTo(DominoTileState.Started)
    }

    @Test
    fun `isRunning return true if tile was started`() {
        tile.updateState(DominoTileState.Started)

        assertThat(tile.isRunning).isTrue
    }

    @Test
    fun `isRunning return false if tile was not started`() {
        assertThat(tile.isRunning).isFalse
    }

    @Test
    fun `isRunning return false if tile had error`() {
        tile.updateState(DominoTileState.StoppedDueToError)

        assertThat(tile.isRunning).isFalse
    }

    @Test
    fun `update status invoke postCustomEventToFollowers when status had changed`() {
        tile.updateState(DominoTileState.StoppedByParent)

        verify(coordinator).postCustomEventToFollowers(StatusChangeEvent(DominoTileState.StoppedByParent))
    }

    @Test
    fun `update status won't invoke postCustomEventToFollowers when status had not changed`() {
        tile.updateState(DominoTileState.StoppedDueToChildStopped)
        tile.updateState(DominoTileState.StoppedDueToChildStopped)
        tile.updateState(DominoTileState.StoppedDueToChildStopped)

        verify(coordinator, times(1)).postCustomEventToFollowers(StatusChangeEvent(DominoTileState.StoppedDueToChildStopped))
    }

    @Test
    fun `update state to started will send update state with UP`() {
        tile.updateState(DominoTileState.Started)

        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `update state to stopped will send update state with DOWN`() {
        tile.updateState(DominoTileState.StoppedByParent)

        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `update state to error will send update state with ERROR`() {
        tile.updateState(DominoTileState.StoppedDueToError)

        verify(coordinator).updateStatus(LifecycleStatus.ERROR)
    }

    @Test
    fun `update state to created will not send update state`() {
        tile.updateState(DominoTileState.Started)
        tile.updateState(DominoTileState.Created)

        verify(coordinator, times(1)).updateStatus(any(), anyString())
    }
}
