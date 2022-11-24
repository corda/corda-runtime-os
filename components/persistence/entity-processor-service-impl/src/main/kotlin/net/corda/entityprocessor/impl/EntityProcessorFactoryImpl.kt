package net.corda.entityprocessor.impl

import net.corda.entityprocessor.EntityProcessor
import net.corda.entityprocessor.EntityProcessorFactory
import net.corda.entityprocessor.impl.internal.EntityMessageProcessor
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.PayloadChecker
import net.corda.persistence.common.ResponseFactory
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("UNUSED")
@Component(service = [EntityProcessorFactory::class])
class EntityProcessorFactoryImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = EntitySandboxService::class)
    private val entitySandboxService: EntitySandboxService,
    @Reference(service = ResponseFactory::class)
    private val responseFactory: ResponseFactory
) : EntityProcessorFactory {
    companion object {
        internal const val GROUP_NAME = "persistence.entity.processor"
    }

    override fun create(config: SmartConfig): EntityProcessor {
        val subscriptionConfig = SubscriptionConfig(GROUP_NAME, Schemas.Persistence.PERSISTENCE_ENTITY_PROCESSOR_TOPIC)

        val processor = EntityMessageProcessor(
            entitySandboxService,
            responseFactory,
            PayloadChecker(config)::checkSize
        )

        val subscription = subscriptionFactory.createDurableSubscription(
            subscriptionConfig,
            processor,
            config,
            null
        )

        return EntityProcessorImpl(subscription)
    }
}
