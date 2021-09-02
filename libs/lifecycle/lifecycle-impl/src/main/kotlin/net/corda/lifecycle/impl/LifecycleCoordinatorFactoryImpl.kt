package net.corda.lifecycle.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleException
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope

@Component(service = [LifecycleCoordinatorFactory::class], scope = ServiceScope.SINGLETON)
class LifecycleCoordinatorFactoryImpl : LifecycleCoordinatorFactory {

    private val registry = LifecycleRegistryImpl()

    override fun createCoordinator(
        name: LifecycleCoordinatorName,
        batchSize: Int,
        handler: LifecycleEventHandler
    ): LifecycleCoordinator {
        if (batchSize <= 0) {
            throw LifecycleException(
                "Failed to create a lifecycle coordinator with name $name as the batch size is less" +
                        " than 1. (Provided batch size was $batchSize)"
            )
        }
        val coordinator = LifecycleCoordinatorImpl(name, batchSize, registry, handler)
        registry.registerCoordinator(name, coordinator)
        return coordinator
    }
}