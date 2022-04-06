package net.corda.crypto.service.impl.flow

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.CryptoOpsProxyClient
import net.corda.crypto.service.CryptoFlowOpsService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.config.toMessagingConfig
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Crypto.Companion.FLOW_OPS_MESSAGE_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CryptoFlowOpsService::class])
class CryptoFlowOpsServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = CryptoOpsProxyClient::class)
    private val cryptoOpsClient: CryptoOpsProxyClient,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService
) : AbstractConfigurableComponent<CryptoFlowOpsServiceImpl.Impl>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<CryptoFlowOpsService>(),
    configurationReadService,
    InactiveImpl(),
    setOf(
        LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
    )
), CryptoFlowOpsService {
    private companion object {
        const val GROUP_NAME = "crypto.ops.flow"
    }

    interface Impl : AutoCloseable {
        val subscription: Subscription<String, FlowOpsRequest>
    }

    override fun createInactiveImpl(): Impl = InactiveImpl()

    override fun createActiveImpl(event: ConfigChangedEvent): Impl {
        logger.info("Creating durable subscription for '{}' topic", FLOW_OPS_MESSAGE_TOPIC)
        val messagingConfig = event.config.toMessagingConfig()
        val processor = CryptoFlowOpsProcessor(
            cryptoOpsClient = cryptoOpsClient
        )
        val bootConfig = event.config[ConfigKeys.BOOT_CONFIG]
        val instanceId = if (bootConfig?.hasPath("instanceId") == true) bootConfig.getInt("instanceId") else 1
        impl.close()
        return ActiveImpl(
            subscriptionFactory.createDurableSubscription(
                subscriptionConfig = SubscriptionConfig(
                    groupName = GROUP_NAME,
                    eventTopic = FLOW_OPS_MESSAGE_TOPIC,
                    instanceId = instanceId
                ),
                processor = processor,
                nodeConfig = messagingConfig,
                partitionAssignmentListener = null
            ).also { it.start() }
        )
    }

    internal class InactiveImpl : Impl {
        override val subscription: Subscription<String, FlowOpsRequest>
            get() = throw IllegalStateException("Component is in illegal state.")

        override fun close() = Unit
    }

    internal class ActiveImpl(
        override val subscription: Subscription<String, FlowOpsRequest>
    ) : Impl {
        override fun close() {
            subscription.close()
        }
    }
}