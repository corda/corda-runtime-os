package net.corda.lifecycle

import net.corda.lifecycle.impl.LifecycleCoordinatorImpl

class LifecycleCoordinatorFactory {

    companion object {
        fun createCoordinator(name: String, batchSize: Int, handler: LifecycleEventHandler) : LifecycleCoordinator {
            return LifecycleCoordinatorImpl(name, batchSize, handler)
        }
    }
}