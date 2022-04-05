package net.corda.crypto.service.impl.rpc

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.service.CryptoOpsService
import net.corda.crypto.service.SigningServiceFactory
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.config.toMessagingConfig
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CryptoOpsService::class])
class CryptoOpsServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = SigningServiceFactory::class)
    private val signingFactory: SigningServiceFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService
) : CryptoOpsService {
    private companion object {
        private val logger = contextLogger()
        const val GROUP_NAME = "crypto.ops.rpc"
        const val CLIENT_NAME = "crypto.ops.rpc"
    }

    internal val lifecycleCoordinator =
        coordinatorFactory.createCoordinator<CryptoOpsService>(::eventHandler)

    @Volatile
    private var configHandle: AutoCloseable? = null

    @Volatile
    private var registrationHandle: RegistrationHandle? = null

    @Volatile
    internal var subscription: RPCSubscription<RpcOpsRequest, RpcOpsResponse>? = null

    override val isRunning: Boolean get() = lifecycleCoordinator.isRunning

    override fun start() {
        logger.info("Starting...")
        lifecycleCoordinator.start()
    }

    override fun stop() {
        logger.info("Stopping...")
        lifecycleCoordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Received event {}", event)
        when (event) {
            is StartEvent -> {
                logger.info("Received start event, starting wait for UP event from dependencies.")
                registrationHandle?.close()
                registrationHandle = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<SigningServiceFactory>(),
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                    )
                )
            }
            is StopEvent -> {
                registrationHandle?.close()
                registrationHandle = null
                configHandle?.close()
                configHandle = null
                deactivate()
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    logger.info("Registering for configuration updates.")
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(MESSAGING_CONFIG, BOOT_CONFIG)
                    )
                } else {
                    configHandle?.close()
                    configHandle = null
                    deactivate()
                    logger.info("Setting status DOWN.")
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                }
            }
            is ConfigChangedEvent -> {
                activate(event)
                logger.info("Setting status UP.")
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            else -> {
                logger.warn("Unexpected event $event!")
            }
        }
    }

    private fun deactivate() {
        val current = subscription
        subscription = null
        current?.close()
    }

    private fun activate(event: ConfigChangedEvent) {
        logger.info("Creating RPC subscription for '{}' topic", Schemas.Crypto.RPC_OPS_MESSAGE_TOPIC)
        val messagingConfig = event.config.toMessagingConfig()
        val processor = CryptoOpsRpcProcessor(signingFactory)
        val current = subscription
        subscription = subscriptionFactory.createRPCSubscription(
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
        current?.close()
    }
}