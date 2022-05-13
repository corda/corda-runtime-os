package net.corda.lifecycle.test.impl

import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.RegistrationHandle

class TestRegistration(
    val coordinators: Set<LifecycleCoordinatorName>,
    private val coordinatorUnderTest: TestLifecycleCoordinatorImpl,
    ) : RegistrationHandle {
    override fun close() {
        coordinatorUnderTest.registrations.remove(this)
    }
}
