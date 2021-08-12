package net.corda.lifecycle.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class RegistrationTest {

    @Test
    fun `up events are delivered only when all coordinators are up`() {
        val harness = RegistrationTestHarness()
        harness.setDependentStatuses(listOf(LifecycleStatus.UP, LifecycleStatus.DOWN, LifecycleStatus.DOWN))
        harness.verifyDeliveredEvents(0, 0)
        harness.setDependentStatuses(listOf(LifecycleStatus.UP, LifecycleStatus.UP, LifecycleStatus.DOWN))
        harness.verifyDeliveredEvents(0, 0)
        harness.setDependentStatuses(listOf(LifecycleStatus.UP, LifecycleStatus.UP, LifecycleStatus.UP))
        harness.verifyDeliveredEvents(1, 0)
    }

    @Test
    fun `down events are delivered if any coordinator goes down`() {
        val harness = RegistrationTestHarness()
        harness.setDependentStatuses(listOf(LifecycleStatus.UP, LifecycleStatus.UP, LifecycleStatus.UP))
        harness.verifyDeliveredEvents(1, 0)
        harness.setDependentStatuses(listOf(LifecycleStatus.DOWN, LifecycleStatus.UP, LifecycleStatus.UP))
        harness.verifyDeliveredEvents(1, 1)
        harness.setDependentStatuses(listOf(LifecycleStatus.DOWN, LifecycleStatus.DOWN, LifecycleStatus.UP))
        harness.verifyDeliveredEvents(1, 1)
    }

    @Test
    fun `closing registration triggers unregistration from children and prevents subsequent delivery`() {
        val harness = RegistrationTestHarness()
        harness.setDependentStatuses(listOf(LifecycleStatus.DOWN, LifecycleStatus.UP, LifecycleStatus.UP))
        harness.closeRegistration()
        harness.setDependentStatuses(listOf(LifecycleStatus.UP, LifecycleStatus.UP, LifecycleStatus.UP))
        harness.verifyDeliveredEvents(0, 0)
    }

    @Test
    fun `going through a series of up and down events triggers events to be sent each time`() {
        val harness = RegistrationTestHarness()
        harness.setDependentStatuses(listOf(LifecycleStatus.UP, LifecycleStatus.UP, LifecycleStatus.UP))
        harness.verifyDeliveredEvents(1, 0)
        harness.setDependentStatuses(listOf(LifecycleStatus.DOWN, LifecycleStatus.UP, LifecycleStatus.UP))
        harness.verifyDeliveredEvents(1, 1)
        harness.setDependentStatuses(listOf(LifecycleStatus.UP, LifecycleStatus.UP, LifecycleStatus.UP))
        harness.verifyDeliveredEvents(2, 1)
        harness.setDependentStatuses(listOf(LifecycleStatus.DOWN, LifecycleStatus.DOWN, LifecycleStatus.UP))
        harness.verifyDeliveredEvents(2, 2)
        harness.setDependentStatuses(listOf(LifecycleStatus.UP, LifecycleStatus.DOWN, LifecycleStatus.UP))
        harness.verifyDeliveredEvents(2, 2)
    }

    @Test
    fun `sending duplicate up or down events does not cause an active status change to be delivered`() {
        val harness = RegistrationTestHarness()
        harness.setDependentStatuses(listOf(LifecycleStatus.UP, LifecycleStatus.UP, LifecycleStatus.UP))
        harness.verifyDeliveredEvents(1, 0)
        harness.setDependentStatuses(listOf(LifecycleStatus.UP, LifecycleStatus.UP, LifecycleStatus.UP))
        harness.verifyDeliveredEvents(1, 0)
        harness.setDependentStatuses(listOf(LifecycleStatus.DOWN, LifecycleStatus.DOWN, LifecycleStatus.DOWN))
        harness.verifyDeliveredEvents(1, 1)
        harness.setDependentStatuses(listOf(LifecycleStatus.DOWN, LifecycleStatus.DOWN, LifecycleStatus.DOWN))
        harness.verifyDeliveredEvents(1, 1)
    }

    @Test
    fun `closing a registration twice does not deliver a second cancel event`() {
        val harness = RegistrationTestHarness()
        harness.closeRegistration() // Asserts that the first close does deliver
        harness.closeRegistration() // On the second close shouldn't have been any extra events, same assertion applies
    }

    private inner class RegistrationTestHarness {
        private val childMocks = listOf<LifecycleCoordinator>(mock(), mock(), mock())
        private val listeningMock = mock<LifecycleCoordinator>()
        private val registration = Registration(childMocks.toSet(), listeningMock)

        fun setDependentStatuses(statuses: List<LifecycleStatus>) {
            childMocks.zip(statuses).forEach {
                registration.updateCoordinatorState(it.first, it.second)
            }
        }

        fun verifyDeliveredEvents(numUp: Int, numDown: Int) {
            verify(listeningMock, times(numUp)).postEvent(
                RegistrationStatusChangeEvent(
                    registration,
                    LifecycleStatus.UP
                )
            )
            verify(listeningMock, times(numDown)).postEvent(
                RegistrationStatusChangeEvent(
                    registration,
                    LifecycleStatus.DOWN
                )
            )
        }

        fun closeRegistration() {
            registration.close()
            childMocks.forEach {
                verify(it).postEvent(CancelRegistration(registration))
            }
        }
    }
}