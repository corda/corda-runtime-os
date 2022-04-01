package net.corda.crypto.service.impl.flow

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.CryptoOpsProxyClient
import net.corda.crypto.service.CryptoFlowOpsService
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
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
    private val configurationReadService: ConfigurationReadService
) : CryptoFlowOpsService {
    private companion object {
        private val logger = contextLogger()
        const val GROUP_NAME = "crypto.ops.flow"
    }

    private val lifecycleCoordinator =
        coordinatorFactory.createCoordinator<CryptoFlowOpsService>(::eventHandler)

    @Volatile
    private var configHandle: AutoCloseable? = null

    @Volatile
    private var registrationHandle: RegistrationHandle? = null

    @Volatile
    private var subscription: Subscription<String, FlowOpsRequest>? = null

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
                        LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                    )
                )
            }
            is StopEvent -> {
                registrationHandle?.close()
                registrationHandle = null
                configHandle?.close()
                configHandle = null
                deleteResources()
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    logger.info("Registering for configuration updates.")
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(ConfigKeys.MESSAGING_CONFIG, ConfigKeys.BOOT_CONFIG)
                    )
                } else {
                    configHandle?.close()
                    configHandle = null
                    deleteResources()
                    logger.info("Setting status DOWN.")
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                }
            }
            is ConfigChangedEvent -> {
                createResources(event)
                logger.info("Setting status UP.")
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            else -> {
                logger.warn("Unexpected event $event!")
            }
        }
    }

    private fun deleteResources() {
        val current = subscription
        subscription = null
        if(current != null) {
            logger.info("Closing durable subscription for '{}' topic", FLOW_OPS_MESSAGE_TOPIC)
            current.close()
        }
    }

    private fun createResources(event: ConfigChangedEvent) {
        logger.info("Creating durable subscription for '{}' topic", FLOW_OPS_MESSAGE_TOPIC)
        val messagingConfig = event.config.toMessagingConfig()
        val processor = CryptoFlowOpsProcessor(
            cryptoOpsClient = cryptoOpsClient
        )
        subscription?.close()
        subscription = subscriptionFactory.createDurableSubscription(
            subscriptionConfig = SubscriptionConfig(
                groupName = GROUP_NAME,
                eventTopic = FLOW_OPS_MESSAGE_TOPIC
            ),
            processor = processor,
            nodeConfig = messagingConfig,
            partitionAssignmentListener = null
        ).also { it.start() }
    }
}