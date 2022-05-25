package net.corda.lifecycle.test.impl

import net.corda.lifecycle.LifecycleCoordinatorScheduler
import net.corda.lifecycle.LifecycleCoordinatorSchedulerFactory

class TestLifecycleCoordinatorSchedulerFactory : LifecycleCoordinatorSchedulerFactory {
    override fun create(): LifecycleCoordinatorScheduler {
        return TestLifecycleCoordinatorScheduler()
    }
}
