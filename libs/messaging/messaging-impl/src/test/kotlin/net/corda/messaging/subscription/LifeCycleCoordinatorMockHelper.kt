package net.corda.messaging.subscription

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleException
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Lifecycle coordinator throws if used when closed, we must mock that behaviour here to ensure the implementation under
 * test does not call lifecycleCoordinator improperly. Use this class to create your mock [LifecycleCoordinator] and
 * then at the end of any tests which are validating lifecycle behaviour (e.g. error condition tests) assert that the
 * [LifecycleCoordinator] was never used after being closed by asserting that [lifecycleCoordinatorThrows] is false.
 */
internal class LifeCycleCoordinatorMockHelper {
    private var _lifecycleCoordinatorThrows = false
    val lifecycleCoordinatorThrows: Boolean
        get() = _lifecycleCoordinatorThrows

    val lifecycleCoordinator: LifecycleCoordinator = mock()

    private var lifecycleCoordinatorClosed = false

    init {
        doAnswer {
            lifecycleCoordinatorClosed = true
        }.whenever(lifecycleCoordinator).close()

        doAnswer {
            checkLifecycleCoordinatorCloseStatus()
        }.whenever(lifecycleCoordinator).postEvent(any())

        doAnswer {
            checkLifecycleCoordinatorCloseStatus()
        }.whenever(lifecycleCoordinator).setTimer(any(), any(), any())

        doAnswer {
            checkLifecycleCoordinatorCloseStatus()
        }.whenever(lifecycleCoordinator).cancelTimer(any())

        doAnswer {
            checkLifecycleCoordinatorCloseStatus()
        }.whenever(lifecycleCoordinator).updateStatus(any(), any())

        doAnswer {
            checkLifecycleCoordinatorCloseStatus()
        }.whenever(lifecycleCoordinator).postCustomEventToFollowers(any())

        doAnswer {
            checkLifecycleCoordinatorCloseStatus()
        }.whenever(lifecycleCoordinator).followStatusChanges(any())

        doAnswer {
            checkLifecycleCoordinatorCloseStatus()
        }.whenever(lifecycleCoordinator).followStatusChangesByName(any())
    }

    private fun checkLifecycleCoordinatorCloseStatus() {
        if (lifecycleCoordinatorClosed) {
            _lifecycleCoordinatorThrows = true
            throw LifecycleException("")
        }
    }
}
