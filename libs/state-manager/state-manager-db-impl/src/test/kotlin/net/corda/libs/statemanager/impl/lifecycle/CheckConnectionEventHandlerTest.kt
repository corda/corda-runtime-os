package net.corda.libs.statemanager.impl.lifecycle

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleException
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.lang.IllegalStateException

class CheckConnectionEventHandlerTest {
    private val coordinatorName = mock<LifecycleCoordinatorName>()
    private val lifecycleCoordinator = mock<LifecycleCoordinator>()

    @Test
    fun scheduleConnectionCheckHandleExceptions() {
        whenever(lifecycleCoordinator.setTimer(any(), any(), any())).thenThrow(LifecycleException("Mock"))
        val connectionEventHandler = CheckConnectionEventHandler(coordinatorName) {}

        Assertions.assertThatCode {
            connectionEventHandler.scheduleConnectionCheck(lifecycleCoordinator)
        }.doesNotThrowAnyException()
    }

    @Test
    fun processStartEventSchedulesConnectionCheck() {
        val connectionEventHandler = CheckConnectionEventHandler(coordinatorName) {}

        connectionEventHandler.processEvent(StartEvent(), lifecycleCoordinator)
        verify(lifecycleCoordinator).setTimer(eq(CheckConnectionEventHandler.CHECK_EVENT_KEY), eq(0), any())
        verifyNoMoreInteractions(lifecycleCoordinator)
    }

    @Test
    fun processStopEventCancelsScheduledConnectionCheck() {
        val connectionEventHandler = CheckConnectionEventHandler(coordinatorName) {}

        connectionEventHandler.processEvent(StopEvent(), lifecycleCoordinator)
        verify(lifecycleCoordinator).cancelTimer(CheckConnectionEventHandler.CHECK_EVENT_KEY)
        verifyNoMoreInteractions(lifecycleCoordinator)
    }

    @Test
    fun processCheckConnectionEventScheduledNextConnectionCheckAndSetStatusAsUpIfConnectionCheckSucceeds() {
        val connectionEventHandler = CheckConnectionEventHandler(coordinatorName) {}

        connectionEventHandler.processEvent(
            CheckConnectionEvent(CheckConnectionEventHandler.CHECK_EVENT_KEY),
            lifecycleCoordinator
        )
        verify(lifecycleCoordinator).updateStatus(LifecycleStatus.UP, "Connection check passed")
        verify(lifecycleCoordinator).setTimer(
            eq(CheckConnectionEventHandler.CHECK_EVENT_KEY),
            eq(CheckConnectionEventHandler.interval.toMillis()),
            any()
        )
        verifyNoMoreInteractions(lifecycleCoordinator)
    }

    @Test
    fun processCheckConnectionEventScheduledNextConnectionCheckAndSetStatusAsDownIfConnectionCheckFails() {
        val exception = IllegalStateException("Connection Check Failure")
        val connectionEventHandler = CheckConnectionEventHandler(coordinatorName) {
            throw exception
        }

        connectionEventHandler.processEvent(
            CheckConnectionEvent(CheckConnectionEventHandler.CHECK_EVENT_KEY),
            lifecycleCoordinator
        )
        verify(lifecycleCoordinator).updateStatus(LifecycleStatus.DOWN, "Connection check failed: $exception")
        verify(lifecycleCoordinator).setTimer(
            eq(CheckConnectionEventHandler.CHECK_EVENT_KEY),
            eq(CheckConnectionEventHandler.interval.toMillis()),
            any()
        )
        verifyNoMoreInteractions(lifecycleCoordinator)
    }
}
