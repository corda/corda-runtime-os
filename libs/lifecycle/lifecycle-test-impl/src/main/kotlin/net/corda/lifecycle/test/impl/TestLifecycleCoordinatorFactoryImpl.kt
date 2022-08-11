package net.corda.lifecycle.test.impl

import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryCoordinatorAccess
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl

class TestLifecycleCoordinatorFactoryImpl(
    val registry: LifecycleRegistryCoordinatorAccess = LifecycleRegistryImpl(),
    private val factory: LifecycleCoordinatorFactory = LifecycleCoordinatorFactoryImpl(
        registry,
        TestLifecycleCoordinatorSchedulerFactory()
    )
) : LifecycleCoordinatorFactory by factory {

    var dependentComponents: DependentComponents? = null

    override fun createCoordinator(
        name: LifecycleCoordinatorName,
        batchSize: Int,
        dependentComponents: DependentComponents?,
        handler: LifecycleEventHandler
    ): LifecycleCoordinator {

        this.dependentComponents = dependentComponents

        return factory.createCoordinator(
            name,
            batchSize,
            dependentComponents,
            handler
        )
    }

}
