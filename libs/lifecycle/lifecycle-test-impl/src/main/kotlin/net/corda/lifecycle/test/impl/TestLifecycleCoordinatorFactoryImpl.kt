package net.corda.lifecycle.test.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler

class TestLifecycleCoordinatorFactoryImpl : LifecycleCoordinatorFactory {

    private var _lifecycleCoordinator: TestLifecycleCoordinatorImpl? = null
    val lifecycleCoordinator: TestLifecycleCoordinatorImpl
        get() = _lifecycleCoordinator!!

    override fun createCoordinator(
        name: LifecycleCoordinatorName,
        batchSize: Int,
        handler: LifecycleEventHandler
    ): LifecycleCoordinator {
        _lifecycleCoordinator = TestLifecycleCoordinatorImpl(
            name,
            handler
        )
        return lifecycleCoordinator
    }
}
