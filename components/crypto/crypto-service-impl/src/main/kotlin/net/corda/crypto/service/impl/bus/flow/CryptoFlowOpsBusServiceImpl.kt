package net.corda.crypto.service.impl.bus.flow

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.CryptoOpsProxyClient
import net.corda.crypto.service.CryptoFlowOpsBusService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.libs.configuration.helper.getConfig
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Crypto.Companion.FLOW_OPS_MESSAGE_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CryptoFlowOpsBusService::class])
class CryptoFlowOpsBusServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = CryptoOpsProxyClient::class)
    private val cryptoOpsClient: CryptoOpsProxyClient,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService
) : AbstractConfigurableComponent<CryptoFlowOpsBusServiceImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<CryptoFlowOpsBusService>(),
    configurationReadService = configurationReadService,
    impl = InactiveImpl(),
    upstream = setOf(
        LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
    ),
    configKeys = setOf(
        MESSAGING_CONFIG,
        BOOT_CONFIG,
        CRYPTO_CONFIG
    )
), CryptoFlowOpsBusService {
    private companion object {
        const val GROUP_NAME = "crypto.ops.flow"
    }

    interface Impl : AutoCloseable {
        val subscription: Subscription<String, FlowOpsRequest>
    }

    override fun createInactiveImpl(): Impl = InactiveImpl()

    override fun createActiveImpl(event: ConfigChangedEvent): Impl {
        logger.info("Creating durable subscription for '{}' topic", FLOW_OPS_MESSAGE_TOPIC)
        val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
        val processor = CryptoFlowOpsBusProcessor(cryptoOpsClient, event)
        return ActiveImpl(
            subscriptionFactory.createDurableSubscription(
                subscriptionConfig = SubscriptionConfig(
                    groupName = GROUP_NAME,
                    eventTopic = FLOW_OPS_MESSAGE_TOPIC
                ),
                processor = processor,
                messagingConfig = messagingConfig,
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