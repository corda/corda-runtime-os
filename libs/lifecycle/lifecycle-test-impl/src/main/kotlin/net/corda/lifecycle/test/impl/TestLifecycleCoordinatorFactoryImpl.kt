package net.corda.lifecycle.test.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler

class TestLifecycleCoordinatorFactoryImpl : LifecycleCoordinatorFactory {

    val coordinators = mutableMapOf<LifecycleCoordinatorName, TestLifecycleCoordinatorImpl>()

    override fun createCoordinator(
        name: LifecycleCoordinatorName,
        batchSize: Int,
        handler: LifecycleEventHandler
    ): LifecycleCoordinator {
        return coordinators.compute(name) { _, _ ->
            TestLifecycleCoordinatorImpl(name, handler)
        } as LifecycleCoordinator
    }
}
