package net.corda.lifecycle.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope

@Component(service = [LifecycleCoordinatorFactory::class], scope = ServiceScope.SINGLETON)
class LifecycleCoordinatorFactoryImpl : LifecycleCoordinatorFactory {

    private val registry = LifecycleRegistryImpl()

    override fun createCoordinator(name: String, batchSize: Int, handler: LifecycleEventHandler): LifecycleCoordinator {
        val coordinator = LifecycleCoordinatorImpl(name, batchSize, handler)
        registry.registerCoordinator(name, coordinator)
        return coordinator
    }
}