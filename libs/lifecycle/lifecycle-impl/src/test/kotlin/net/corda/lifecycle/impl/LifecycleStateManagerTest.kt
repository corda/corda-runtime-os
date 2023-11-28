package net.corda.lifecycle.impl

import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.concurrent.ScheduledFuture

class LifecycleStateManagerTest {
    private val manager = LifecycleStateManager(10)

    @Test
    fun `cancelAllTimer will cancel all the timers`() {
        val future1 = mock<ScheduledFuture<*>>()
        val future2 = mock<ScheduledFuture<*>>()
        manager.setTimer("one", future1)
        manager.setTimer("two", future2)

        manager.cancelAllTimer()

        verify(future1).cancel(true)
        verify(future2).cancel(true)
    }

    @Test
    fun `cancelAllTimer will forget about the timers`() {
        val future1 = mock<ScheduledFuture<*>>()
        val future2 = mock<ScheduledFuture<*>>()
        manager.setTimer("one", future1)
        manager.setTimer("two", future2)

        manager.cancelAllTimer()

        manager.cancelTimer("one")
        verify(future1, times(1)).cancel(true)
    }
}
