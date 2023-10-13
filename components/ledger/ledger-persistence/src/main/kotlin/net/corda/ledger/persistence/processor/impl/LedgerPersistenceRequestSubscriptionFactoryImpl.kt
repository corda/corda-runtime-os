package net.corda.ledger.persistence.processor.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.ledger.persistence.processor.DelegatedRequestHandlerSelector
import net.corda.ledger.persistence.processor.LedgerPersistenceRequestProcessor
import net.corda.ledger.persistence.processor.LedgerPersistenceRequestSubscriptionFactory
import net.corda.ledger.persistence.processor.LedgerPersistenceRpcRequestProcessor
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.config.SyncRPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.ResponseFactory
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [LedgerPersistenceRequestSubscriptionFactory::class])
@Suppress("LongParameterList")
class LedgerPersistenceRequestSubscriptionFactoryImpl @Activate constructor(
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = EntitySandboxService::class)
    private val entitySandboxService: EntitySandboxService,
    @Reference(service = DelegatedRequestHandlerSelector::class)
    private val delegatedRequestHandlerSelector: DelegatedRequestHandlerSelector,
    @Reference(service = ResponseFactory::class)
    private val responseFactory: ResponseFactory
) : LedgerPersistenceRequestSubscriptionFactory {
    companion object {
        internal const val GROUP_NAME = "persistence.ledger.processor"
        const val SUBSCRIPTION_NAME = "Ledger"
        const val PATH = "/ledger"
    }

    override fun create(config: SmartConfig): Subscription<String, LedgerPersistenceRequest> {
        val subscriptionConfig = SubscriptionConfig(GROUP_NAME, Schemas.Persistence.PERSISTENCE_LEDGER_PROCESSOR_TOPIC)

        val processor = LedgerPersistenceRequestProcessor(
            currentSandboxGroupContext,
            entitySandboxService,
            delegatedRequestHandlerSelector,
            responseFactory
        )

        return subscriptionFactory.createDurableSubscription(
            subscriptionConfig,
            processor,
            config,
            null
        )
    }

    override fun createRpcSubscription(): RPCSubscription<LedgerPersistenceRequest, FlowEvent> {
        val processor = LedgerPersistenceRpcRequestProcessor(
            currentSandboxGroupContext,
            entitySandboxService,
            delegatedRequestHandlerSelector,
            responseFactory,
            LedgerPersistenceRequest::class.java,
            FlowEvent::class.java
        )
        val rpcConfig = SyncRPCConfig(SUBSCRIPTION_NAME, PATH)
        return subscriptionFactory.createHttpRPCSubscription(rpcConfig, processor)
    }
}
