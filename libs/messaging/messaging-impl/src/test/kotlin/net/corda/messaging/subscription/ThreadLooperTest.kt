package net.corda.messaging.subscription

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.config.ResolvedSubscriptionConfig
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class ThreadLooperTest {
    private val config = mock<ResolvedSubscriptionConfig> {
        on { lifecycleCoordinatorName } doReturn LifecycleCoordinatorName("NAME")
    }
    private val coordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) } doReturn coordinator
    }
    private val thread = mock<Thread>()

    private val looper = ThreadLooper(
        mock(),
        config,
        lifecycleCoordinatorFactory,
        "",
        { },
        { _, block ->
            block()
            thread
        },
        callUntilStopped = false
    )

    @Test
    fun `updateLifecycleStatus will update a running coordinate`() {
        looper.updateLifecycleStatus(LifecycleStatus.UP)

        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `updateLifecycleStatus will not update a closed coordinate`() {
        looper.start()
        looper.updateLifecycleStatus(LifecycleStatus.UP)

        verify(coordinator, never()).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `updateLifecycleStatus with reason will update a running coordinate`() {
        looper.updateLifecycleStatus(LifecycleStatus.UP, "reason")

        verify(coordinator).updateStatus(LifecycleStatus.UP, "reason")
    }

    @Test
    fun `updateLifecycleStatus with reason will not update a closed coordinate`() {
        looper.start()
        looper.updateLifecycleStatus(LifecycleStatus.UP, "reason")

        verify(coordinator, never()).updateStatus(LifecycleStatus.UP, "reason")
    }

    @Test
    fun `runConsumeLoop will close the coordinator`() {
        looper.start()

        verify(coordinator).close()
    }
}
