package net.corda.crypto.client

import net.corda.crypto.component.lifecycle.AbstractComponent
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [HSMLabelMapComponent::class])
class HSMLabelMapComponentImpl(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory
) : AbstractComponent<HSMLabelMapComponentImpl.Resources>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<HSMLabelMapComponent>()
), HSMLabelMapComponent {

    private lateinit var subscriptionFactory: SubscriptionFactory

    @Reference(service = SubscriptionFactory::class)
    fun publisherFactoryRef(subscriptionFactory: SubscriptionFactory) {
        this.subscriptionFactory = subscriptionFactory
        createResources()
    }

    override fun get(tenantId: String, category: String) {
        TODO("Not yet implemented")
    }

    override fun allocateResources(): Resources {
        TODO("Not yet implemented")
    }

    class Resources : AutoCloseable {
        override fun close() {
            TODO("Not yet implemented")
        }

    }
}