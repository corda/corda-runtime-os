package net.corda.entityprocessor.impl

import net.corda.entityprocessor.EntityProcessorFactory
import net.corda.entityprocessor.EntityProcessor
import net.corda.entityprocessor.impl.internal.EntityMessageProcessor
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.PayloadChecker
import net.corda.schema.Schemas
import net.corda.schema.configuration.MessagingConfig
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
    private val entitySandboxService: EntitySandboxService,
    @Reference(service = ExternalEventResponseFactory::class)
    private val externalEventResponseFactory: ExternalEventResponseFactory
) : EntityProcessorFactory {
    companion object {
        internal const val GROUP_NAME = "virtual.node.entity.processor"
        private const val CORDA_MESSAGE_OVERHEAD = 1024
    }

    override fun create(config: SmartConfig): EntityProcessor {
        val subscriptionConfig = SubscriptionConfig(GROUP_NAME, Schemas.VirtualNode.ENTITY_PROCESSOR)
        // max allowed msg size minus headroom for wrapper message
        val maxPayLoadSize = config.getInt(MessagingConfig.MAX_ALLOWED_MSG_SIZE) - CORDA_MESSAGE_OVERHEAD
        val processor = EntityMessageProcessor(
            entitySandboxService,
            externalEventResponseFactory,
            PayloadChecker(maxPayLoadSize)::checkSize
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
