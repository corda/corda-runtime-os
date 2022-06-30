package net.corda.crypto.service.impl.bus.rpc

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.service.CryptoOpsBusService
import net.corda.crypto.service.SigningServiceFactory
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
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

@Component(service = [CryptoOpsBusService::class])
class CryptoOpsBusServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = SigningServiceFactory::class)
    private val signingFactory: SigningServiceFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService
) : AbstractConfigurableComponent<CryptoOpsBusServiceImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<CryptoOpsBusService>(),
    configurationReadService = configurationReadService,
    impl = InactiveImpl(),
    upstream = setOf(
        LifecycleCoordinatorName.forComponent<SigningServiceFactory>(),
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
    ),
    configKeys = setOf(
        MESSAGING_CONFIG,
        BOOT_CONFIG,
        CRYPTO_CONFIG
    )
), CryptoOpsBusService {
    private companion object {
        const val GROUP_NAME = "crypto.ops.rpc"
        const val CLIENT_NAME = "crypto.ops.rpc"
    }

    interface Impl : AutoCloseable {
        val subscription: RPCSubscription<RpcOpsRequest, RpcOpsResponse>
    }

    override fun createInactiveImpl(): Impl = InactiveImpl()

    override fun createActiveImpl(event: ConfigChangedEvent): Impl {
        logger.info("Creating RPC subscription for '{}' topic", Schemas.Crypto.RPC_OPS_MESSAGE_TOPIC)
        val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
        val processor = CryptoOpsBusProcessor(signingFactory, event)
        return ActiveImpl(
            subscriptionFactory.createRPCSubscription(
                rpcConfig = RPCConfig(
                    groupName = GROUP_NAME,
                    clientName = CLIENT_NAME,
                    requestTopic = Schemas.Crypto.RPC_OPS_MESSAGE_TOPIC,
                    requestType = RpcOpsRequest::class.java,
                    responseType = RpcOpsResponse::class.java
                ),
                responderProcessor = processor,
                messagingConfig = messagingConfig
            ).also { it.start() }
        )
    }

    internal class InactiveImpl : Impl {
        override val subscription: RPCSubscription<RpcOpsRequest, RpcOpsResponse>
            get() = throw IllegalStateException("Component is in illegal state.")

        override fun close() = Unit
    }

    internal class ActiveImpl(
        override val subscription: RPCSubscription<RpcOpsRequest, RpcOpsResponse>
    ) : Impl {
        override fun close() {
            subscription.close()
        }
    }
}