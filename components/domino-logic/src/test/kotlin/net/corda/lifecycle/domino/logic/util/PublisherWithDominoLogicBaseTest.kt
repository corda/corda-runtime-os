package net.corda.lifecycle.domino.logic.util

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class PublisherWithDominoLogicBaseTest {

    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        var currentStatus: LifecycleStatus = LifecycleStatus.DOWN
        on { postEvent(any()) } doAnswer {
            handler.lastValue.processEvent(it.getArgument(0) as LifecycleEvent, mock)
        }
        on { start() } doAnswer {
            handler.lastValue.processEvent(StartEvent(), mock)
        }
        on { stop() } doAnswer {
            handler.lastValue.processEvent(StopEvent(), mock)
        }
        on { updateStatus(any(), any()) } doAnswer { currentStatus =  it.getArgument(0) }
        on { status } doAnswer { currentStatus }
    }
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }

    private val autoCloseable = mock<AutoCloseable>()

    private var factoryCalled = false
    private fun factory(): AutoCloseable {
        factoryCalled = true
        return autoCloseable
    }

    private val wrapper = PublisherWithDominoLogicBase(coordinatorFactory, ::factory)

    @Test
    fun `start will start the publisher`() {
        wrapper.start()

        assertThat(factoryCalled).isTrue
    }

    @Test
    fun `start will set the state to up`() {
        wrapper.start()

        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `stop will close the autoClosable`() {
        wrapper.start()
        wrapper.stop()

        verify(autoCloseable).close()
    }

    @Test
    fun `close will remember to close the coordinator`() {
        wrapper.start()
        wrapper.close()

        verify(coordinator).close()
    }

    @Test
    fun `close will remember to close the autoClosable`() {
        wrapper.start()
        wrapper.close()

        verify(autoCloseable).close()
    }

    @Test
    fun `close will not close the autoClosable again if stopped`() {
        wrapper.start()
        wrapper.stop()
        wrapper.close()

        verify(autoCloseable).close()
    }
}
