package net.corda.entityprocessor.impl

import net.corda.entityprocessor.EntityProcessor
import net.corda.entityprocessor.EntityProcessorFactory
import net.corda.entityprocessor.impl.internal.EntityMessageProcessor
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.PayloadChecker
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("UNUSED")
@Component(service = [EntityProcessorFactory::class])
class EntityProcessorFactoryImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = EntitySandboxService::class)
    private val entitySandboxService: EntitySandboxService,
    @Reference(service = ExternalEventResponseFactory::class)
    private val externalEventResponseFactory: ExternalEventResponseFactory
) : EntityProcessorFactory {
    companion object {
        internal const val GROUP_NAME = "persistence.entity.processor"
        private const val CORDA_MESSAGE_OVERHEAD = 1024
    }

    override fun create(config: SmartConfig): EntityProcessor {
        val subscriptionConfig = SubscriptionConfig(GROUP_NAME, Schemas.Persistence.PERSISTENCE_ENTITY_PROCESSOR_TOPIC)

        val processor = EntityMessageProcessor(
            entitySandboxService,
            externalEventResponseFactory,
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
