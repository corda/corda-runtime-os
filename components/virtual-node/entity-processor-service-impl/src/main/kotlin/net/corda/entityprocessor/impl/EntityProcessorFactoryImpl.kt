package net.corda.entityprocessor.impl

import net.corda.entityprocessor.EntityProcessor
import net.corda.entityprocessor.EntityProcessorFactory
import net.corda.entityprocessor.impl.internal.EntityMessageProcessor
import net.corda.entityprocessor.impl.internal.EntitySandboxService
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("UNUSED")
@Component(service = [EntityProcessorFactory::class])
class EntityProcessorFactoryImpl @Activate constructor(
    @Reference
    private val subscriptionFactory: SubscriptionFactory,
    @Reference
    private val publisherFactory: PublisherFactory,
    @Reference
    private val entitySandboxService: EntitySandboxService
) : EntityProcessorFactory {
    companion object {
        internal const val GROUP_NAME = "virtual.node.entity.processor"
    }

    override fun create(config: SmartConfig): EntityProcessor {
        val subscriptionConfig = SubscriptionConfig(GROUP_NAME, Schemas.VirtualNode.ENTITY_PROCESSOR)
        val processor = EntityMessageProcessor(entitySandboxService)

        val subscription = subscriptionFactory.createDurableSubscription(
            subscriptionConfig,
            processor,
            config,
            null
        )

        return EntityProcessorImpl(subscription)
    }
}
