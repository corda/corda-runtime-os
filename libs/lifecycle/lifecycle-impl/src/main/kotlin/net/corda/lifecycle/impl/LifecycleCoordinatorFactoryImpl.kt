package net.corda.lifecycle.impl

import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleCoordinatorSchedulerFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleException
import net.corda.lifecycle.impl.registry.LifecycleRegistryCoordinatorAccess
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Component(service = [LifecycleCoordinatorFactory::class], scope = ServiceScope.SINGLETON)
class LifecycleCoordinatorFactoryImpl @Activate constructor(
    @Reference
    private val registry: LifecycleRegistryCoordinatorAccess,
    @Reference
    private val schedulerFactory: LifecycleCoordinatorSchedulerFactory
) : LifecycleCoordinatorFactory {

    override fun createCoordinator(
        name: LifecycleCoordinatorName,
        batchSize: Int,
        dependentComponents: DependentComponents?,
        handler: LifecycleEventHandler
    ): LifecycleCoordinator {
        return internalCreateCoordinator(name, batchSize, dependentComponents, handler)
    }

    private fun internalCreateCoordinator(
        name: LifecycleCoordinatorName,
        batchSize: Int,
        dependentComponents: DependentComponents?,
        handler: LifecycleEventHandler
    ): LifecycleCoordinator {
        if (batchSize <= 0) {
            throw LifecycleException(
                "Failed to create a lifecycle coordinator with name $name as the batch size is less" +
                        " than 1. (Provided batch size was $batchSize)"
            )
        }

        val coordinator = LifecycleCoordinatorImpl(name, batchSize, dependentComponents, registry, schedulerFactory.create(), handler)
        registry.registerCoordinator(name, coordinator)
        return coordinator
    }
}
