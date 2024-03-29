package net.corda.p2p.linkmanager.sessions

import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atMost
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class DeadSessionMonitorTest {
    private val scheduledExecutorService = mock<ScheduledExecutorService>()
    private val sessionCache = mock<SessionCache>()

    private val target = DeadSessionMonitor(scheduledExecutorService, sessionCache)

    @Test
    fun `when a data message is sent called before configuration then throw`() {
        val sessionId = "s1"
        assertThatIllegalStateException().isThrownBy { target.messageSent(sessionId) }
            .withMessage("Data messages sent or acknowledged before configuration has been read.")
    }

    @Test
    fun `when a data message is sent then a session deletion is scheduled for the configured time`() {
        val sessionId = "s1"
        val timeout = 10L
        val deleteActionFuture = mock<ScheduledFuture<Unit>>()

        val deleteAction = argumentCaptor<Runnable>()
        whenever(scheduledExecutorService.schedule(deleteAction.capture(), any(), any()))
            .thenReturn(deleteActionFuture)

        target.onConfigChange(timeout)
        target.messageSent(sessionId)

        verify(scheduledExecutorService).schedule(any(), eq(timeout), eq(TimeUnit.SECONDS))

        // Trigger the scheduled action to validate it clears the cache.
        deleteAction.firstValue.run()
        verify(sessionCache).deleteBySessionId(sessionId)
    }

    @Test
    fun `when a data message and there is an existing action for this session then do nothing`() {
        val sessionId = "s1"
        val timeout = 10L
        val deleteActionFuture1 = mock<ScheduledFuture<Unit>>()
        val deleteActionFuture2 = mock<ScheduledFuture<Unit>>()

        whenever(scheduledExecutorService.schedule(any(), any(), any()))
            .thenReturn(deleteActionFuture1, deleteActionFuture2)

        target.onConfigChange(timeout)
        target.messageSent(sessionId)
        target.messageSent(sessionId)

        // The second call should be discarded as we are already tracking a send action for this session
        verify(scheduledExecutorService, atMost(1)).schedule(any(), any(), any())
    }

    @Test
    fun `when configuration is changed then new deletions use the latest timeout value`() {
        val sessionId = "s1"
        val timeout1 = 10L
        val timeout2 = 20L
        val deleteActionFuture = mock<ScheduledFuture<Unit>>()

        whenever(scheduledExecutorService.schedule(any(), any(), any()))
            .thenReturn(deleteActionFuture)

        target.onConfigChange(timeout1)
        target.messageSent(sessionId)
        verify(scheduledExecutorService).schedule(any(), eq(timeout1), eq(TimeUnit.SECONDS))
        target.ackReceived(sessionId)

        target.onConfigChange(timeout2)
        target.messageSent(sessionId)
        verify(scheduledExecutorService).schedule(any(), eq(timeout2), eq(TimeUnit.SECONDS))
    }

    @Test
    fun `when an ack message is received then any existing deletion action is canceled`() {
        val sessionId = "s1"
        val timeout = 10L
        val deleteActionFuture1 = mock<ScheduledFuture<Unit>>()

        whenever(scheduledExecutorService.schedule(any(), any(), any()))
            .thenReturn(deleteActionFuture1)

        target.onConfigChange(timeout)
        target.messageSent(sessionId)
        target.ackReceived(sessionId)

        verify(deleteActionFuture1).cancel(false)
    }

    @Test
    fun `when a session is removed then any existing deletion action is canceled`() {
        val sessionId = "s1"
        val timeout = 10L
        val deleteActionFuture1 = mock<ScheduledFuture<Unit>>()

        whenever(scheduledExecutorService.schedule(any(), any(), any()))
            .thenReturn(deleteActionFuture1)

        target.onConfigChange(timeout)
        target.messageSent(sessionId)
        target.sessionRemoved(sessionId)

        verify(deleteActionFuture1).cancel(false)
    }
}
