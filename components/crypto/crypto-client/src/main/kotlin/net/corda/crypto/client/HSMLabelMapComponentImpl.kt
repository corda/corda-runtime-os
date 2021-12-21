package net.corda.crypto.client

import net.corda.crypto.HSMLabelMap
import net.corda.crypto.HSMLabelMapComponent
import net.corda.crypto.component.lifecycle.AbstractComponent
import net.corda.crypto.impl.stopGracefully
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.schema.Schemas
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
    companion object {
        const val GROUP_NAME = "crypto.config.hsm.label"

        inline val Resources?.instance: HSMLabelMap
            get() = this?.processor ?: throw IllegalStateException("The component haven't been initialised.")
    }

    @Volatile
    private lateinit var subscriptionFactory: SubscriptionFactory

    @Reference(service = SubscriptionFactory::class)
    fun publisherFactoryRef(subscriptionFactory: SubscriptionFactory) {
        this.subscriptionFactory = subscriptionFactory
        createResources()
    }

    override fun get(tenantId: String, category: String): String =
        resources.instance.get(tenantId, category)

    override fun allocateResources(): Resources =
        Resources(subscriptionFactory)

    class Resources(
        subscriptionFactory: SubscriptionFactory
    ) : AutoCloseable {
        val processor = HSMLabelMapProcessor()
        private val sub = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(GROUP_NAME, Schemas.Crypto.HSM_CONFIG_LABEL_TOPIC),
            processor
        ).also { it.start() }
        override fun close() {
            sub.stopGracefully()
        }
    }
}