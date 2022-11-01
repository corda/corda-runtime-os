package net.corda.ledger.persistence.processor.factory

import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.persistence.processor.PersistenceRequestProcessor
import net.corda.ledger.persistence.processor.MessageHandlerSelector
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.persistence.common.EntitySandboxService
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [PersistenceRequestSubscriptionFactory::class])
@Suppress("LongParameterList")
class PersistenceRequestSubscriptionFactoryImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = EntitySandboxService::class)
    private val entitySandboxService: EntitySandboxService,
    @Reference(service = MessageHandlerSelector::class)
    private val messageHandlerSelector: MessageHandlerSelector,
    @Reference(service = ExternalEventResponseFactory::class)
    private val externalEventResponseFactory: ExternalEventResponseFactory
) : PersistenceRequestSubscriptionFactory {
    companion object {
        internal const val GROUP_NAME = "persistence.ledger.processor"
    }

    override fun create(config: SmartConfig): Subscription<String, LedgerPersistenceRequest> {
        val subscriptionConfig = SubscriptionConfig(GROUP_NAME, Schemas.Persistence.PERSISTENCE_LEDGER_PROCESSOR_TOPIC)

        val processor = PersistenceRequestProcessor(
            entitySandboxService,
            messageHandlerSelector,
            externalEventResponseFactory
        )

        return subscriptionFactory.createDurableSubscription(
            subscriptionConfig,
            processor,
            config,
            null
        )
    }
}
