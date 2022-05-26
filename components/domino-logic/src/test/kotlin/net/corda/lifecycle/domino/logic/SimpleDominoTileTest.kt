package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class SimpleDominoTileTest {
    private val coordinator = mock< LifecycleCoordinator>() {
        var currentStatus: LifecycleStatus = LifecycleStatus.DOWN
        on { updateStatus(any(), any()) } doAnswer { currentStatus =  it.getArgument(0) }
        on { status } doAnswer { currentStatus }
    }
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

        assertThat(tile.state).isEqualTo(LifecycleStatus.DOWN)
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
        tile.updateState(LifecycleStatus.UP)

        assertThat(tile.state).isEqualTo(LifecycleStatus.UP)
    }

    @Test
    fun `isRunning return true if tile was started`() {
        tile.updateState(LifecycleStatus.UP)

        assertThat(tile.isRunning).isTrue
    }

    @Test
    fun `isRunning return false if tile was not started`() {
        assertThat(tile.isRunning).isFalse
    }

    @Test
    fun `isRunning return false if tile had error`() {
        tile.updateState(LifecycleStatus.DOWN)

        assertThat(tile.isRunning).isFalse
    }

    @Test
    fun `update status invoke updateStatus when status had changed`() {
        tile.updateState(LifecycleStatus.UP)
        tile.updateState(LifecycleStatus.DOWN)

        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `update status won't invoke updateStatus when status had not changed`() {
        tile.updateState(LifecycleStatus.UP)
        tile.updateState(LifecycleStatus.DOWN)
        tile.updateState(LifecycleStatus.DOWN)
        tile.updateState(LifecycleStatus.DOWN)

        verify(coordinator, times(1)).updateStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `update state to started will send update state with UP`() {
        tile.updateState(LifecycleStatus.UP)

        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `update state to error will send update state with ERROR`() {
        tile.updateState(LifecycleStatus.ERROR)

        verify(coordinator).updateStatus(LifecycleStatus.ERROR)
    }
}
