package net.corda.entityprocessor.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.persistence.EntityRequest
import net.corda.entityprocessor.EntityRequestSubscriptionFactory
import net.corda.entityprocessor.impl.internal.EntityRequestProcessor
import net.corda.entityprocessor.impl.internal.EntityRpcRequestProcessor
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.constants.WorkerRPCPaths.PERSISTENCE_PATH
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.config.SyncRPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.PayloadChecker
import net.corda.persistence.common.ResponseFactory
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("UNUSED")
@Component(service = [EntityRequestSubscriptionFactory::class])
class EntityRequestSubscriptionFactoryImpl @Activate constructor(
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = EntitySandboxService::class)
    private val entitySandboxService: EntitySandboxService,
    @Reference(service = ResponseFactory::class)
    private val responseFactory: ResponseFactory
) : EntityRequestSubscriptionFactory {
    companion object {
        internal const val GROUP_NAME = "persistence.entity.processor"
        const val SUBSCRIPTION_NAME = "Persistence"
    }

    override fun create(config: SmartConfig): Subscription<String, EntityRequest> {
        val subscriptionConfig = SubscriptionConfig(GROUP_NAME, Schemas.Persistence.PERSISTENCE_ENTITY_PROCESSOR_TOPIC)

        val processor = EntityRequestProcessor(
            currentSandboxGroupContext,
            entitySandboxService,
            responseFactory,
            PayloadChecker(config)::checkSize
        )

        return subscriptionFactory.createDurableSubscription(
            subscriptionConfig,
            processor,
            config,
            null
        )
    }

    override fun createRpcSubscription(): RPCSubscription<EntityRequest, FlowEvent> {
        val processor = EntityRpcRequestProcessor(
            currentSandboxGroupContext,
            entitySandboxService,
            responseFactory,
            EntityRequest::class.java,
            FlowEvent::class.java
        )
        val rpcConfig = SyncRPCConfig(SUBSCRIPTION_NAME, PERSISTENCE_PATH)
        return subscriptionFactory.createHttpRPCSubscription(rpcConfig, processor)
    }
}
