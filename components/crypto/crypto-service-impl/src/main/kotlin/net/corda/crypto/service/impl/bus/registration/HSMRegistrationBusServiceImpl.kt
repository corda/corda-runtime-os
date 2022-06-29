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
import net.corda.libs.configuration.helper.getConfig
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [HSMRegistrationBusService::class])
class HSMRegistrationBusServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = HSMService::class)
    private val hsmService: HSMService
) : AbstractConfigurableComponent<HSMRegistrationBusServiceImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<HSMRegistrationBusService>(),
    configurationReadService = configurationReadService,
    impl = InactiveImpl(),
    dependencies = setOf(
        LifecycleCoordinatorName.forComponent<HSMService>(),
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
    ),
    configKeys = setOf(
        MESSAGING_CONFIG,
        BOOT_CONFIG,
        CRYPTO_CONFIG
    )
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
        val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
        val processor = HSMRegistrationBusProcessor(hsmService, event)
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