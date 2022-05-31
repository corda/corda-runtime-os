package net.corda.lifecycle.impl

import net.corda.lifecycle.LifecycleCoordinatorScheduler
import net.corda.lifecycle.LifecycleCoordinatorSchedulerFactory
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope

@Component(service = [LifecycleCoordinatorSchedulerFactory::class], scope = ServiceScope.SINGLETON)
class LifecycleCoordinatorSchedulerFactoryImpl : LifecycleCoordinatorSchedulerFactory {
    override fun create(): LifecycleCoordinatorScheduler {
        return LifecycleCoordinatorSchedulerImpl()
    }
}

