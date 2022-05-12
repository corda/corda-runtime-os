package net.corda.crypto.service.impl.bus.configuration

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.service.HSMConfigurationBusService
import net.corda.crypto.service.HSMService
import net.corda.data.crypto.wire.hsm.configuration.HSMConfigurationRequest
import net.corda.data.crypto.wire.hsm.configuration.HSMConfigurationResponse
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.config.toMessagingConfig
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [HSMConfigurationBusService::class])
class HSMConfigurationBusServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = HSMService::class)
    private val hsmService: HSMService
) : AbstractConfigurableComponent<HSMConfigurationBusServiceImpl.Impl>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<HSMConfigurationBusService>(),
    configurationReadService,
    InactiveImpl(),
    setOf(
        LifecycleCoordinatorName.forComponent<HSMService>(),
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
    ),
), HSMConfigurationBusService {
    private companion object {
        const val GROUP_NAME = "crypto.hsm.rpc.registration"
        const val CLIENT_NAME = "crypto.hsm.rpc.registration"
    }

    interface Impl : AutoCloseable {
        val subscription: RPCSubscription<HSMConfigurationRequest, HSMConfigurationResponse>
    }

    override fun createInactiveImpl(): Impl = InactiveImpl()

    override fun createActiveImpl(event: ConfigChangedEvent): Impl {
        logger.info("Creating RPC subscription for '{}' topic", Schemas.Crypto.RPC_HSM_CONFIGURATION_MESSAGE_TOPIC)
        val messagingConfig = event.config.toMessagingConfig()
        val processor = HSMConfigurationBusProcessor(hsmService)
        return ActiveImpl(
            subscriptionFactory.createRPCSubscription(
                rpcConfig = RPCConfig(
                    groupName = GROUP_NAME,
                    clientName = CLIENT_NAME,
                    requestTopic = Schemas.Crypto.RPC_HSM_CONFIGURATION_MESSAGE_TOPIC,
                    requestType = HSMConfigurationRequest::class.java,
                    responseType = HSMConfigurationResponse::class.java
                ),
                responderProcessor = processor,
                messagingConfig = messagingConfig
            ).also { it.start() }
        )
    }

    internal class InactiveImpl : Impl {
        override val subscription: RPCSubscription<HSMConfigurationRequest, HSMConfigurationResponse>
            get() = throw IllegalStateException("Component is in illegal state.")

        override fun close() = Unit
    }

    internal class ActiveImpl(
        override val subscription: RPCSubscription<HSMConfigurationRequest, HSMConfigurationResponse>
    ) : Impl {
        override fun close() {
            subscription.close()
        }
    }
}