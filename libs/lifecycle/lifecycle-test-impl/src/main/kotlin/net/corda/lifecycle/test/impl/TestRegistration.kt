package net.corda.lifecycle.test.impl

import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle

class TestRegistration(
    coordinators: Map<LifecycleCoordinatorName, LifecycleStatus>,
    private val coordinatorUnderTest: TestLifecycleCoordinatorImpl,
    ) : RegistrationHandle {
    val coordinators: MutableMap<LifecycleCoordinatorName, LifecycleStatus> = coordinators.toMutableMap()

    override fun close() {
        coordinatorUnderTest.registrations.remove(this)
    }

    /**
     * Change the status for a given coordinator.  Returns true if the registration status changes
     * as a result
     */
    fun changeCoordinatorStatus(
        coordinatorName: LifecycleCoordinatorName,
        status: LifecycleStatus
    ): Boolean {
        val wasUp = isUp()
        coordinators[coordinatorName] = status
        return isUp() != wasUp
    }

    fun isUp() = coordinators.all { it.value == LifecycleStatus.UP }

}
