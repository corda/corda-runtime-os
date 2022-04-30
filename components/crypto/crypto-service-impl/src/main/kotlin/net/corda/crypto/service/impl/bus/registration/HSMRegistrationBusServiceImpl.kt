package net.corda.crypto.service.impl.bus.registration

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.service.HSMRegistrationBusService
import net.corda.crypto.service.HSMService
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationRequest
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationResponse
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.config.toMessagingConfig
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [HSMRegistrationBusService::class])
class HSMRegistrationBusServiceImpl(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = HSMService::class)
    private val hsmService: HSMService
) : AbstractConfigurableComponent<HSMRegistrationBusServiceImpl.Impl>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<HSMRegistrationBusService>(),
    configurationReadService,
    InactiveImpl(),
    setOf(
        LifecycleCoordinatorName.forComponent<HSMService>(),
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
    ),
), HSMRegistrationBusService {
    private companion object {
        const val GROUP_NAME = "crypto.hsm.rpc.registration"
        const val CLIENT_NAME = "crypto.hsm.rpc.registration"
    }

    interface Impl : AutoCloseable {
        val subscription: RPCSubscription<HSMRegistrationRequest, HSMRegistrationResponse>
    }

    override fun createInactiveImpl(): Impl = InactiveImpl()

    override fun createActiveImpl(event: ConfigChangedEvent): Impl {
        logger.info("Creating RPC subscription for '{}' topic", Schemas.Crypto.RPC_HSM_REGISTRATION_MESSAGE_TOPIC)
        val messagingConfig = event.config.toMessagingConfig()
        val processor = HSMRegistrationBusProcessor(hsmService)
        return ActiveImpl(
            subscriptionFactory.createRPCSubscription(
                rpcConfig = RPCConfig(
                    groupName = GROUP_NAME,
                    clientName = CLIENT_NAME,
                    requestTopic = Schemas.Crypto.RPC_HSM_REGISTRATION_MESSAGE_TOPIC,
                    requestType = HSMRegistrationRequest::class.java,
                    responseType = HSMRegistrationResponse::class.java
                ),
                responderProcessor = processor,
                messagingConfig = messagingConfig
            ).also { it.start() }
        )
    }

    internal class InactiveImpl : Impl {
        override val subscription: RPCSubscription<HSMRegistrationRequest, HSMRegistrationResponse>
            get() = throw IllegalStateException("Component is in illegal state.")

        override fun close() = Unit
    }

    internal class ActiveImpl(
        override val subscription: RPCSubscription<HSMRegistrationRequest, HSMRegistrationResponse>
    ) : Impl {
        override fun close() {
            subscription.close()
        }
    }
}