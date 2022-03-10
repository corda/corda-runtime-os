package net.corda.flow.p2p.filter

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.libs.configuration.schema.messaging.INSTANCE_ID
import net.corda.lifecycle.Lifecycle
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
import net.corda.p2p.app.AppMessage
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowP2PFilterService::class], immediate = true)
class FlowP2PFilterService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory
) : Lifecycle {

    private companion object {
        private val logger = contextLogger()
        private const val CONSUMER_GROUP = "FlowSessionFilterConsumer"
    }

    private val coordinator = coordinatorFactory.createCoordinator<FlowP2PFilterService>(::eventHandler)
    private var registration: RegistrationHandle? = null
    private var configHandle: AutoCloseable? = null
    private var durableSub: Subscription<String, AppMessage>? = null

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                logger.info("Starting flow p2p filter component.")
                registration?.close()
                registration =
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                        )
                    )
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                    )
                } else {
                    configHandle?.close()
                }
            }
            is ConfigChangedEvent -> {
                logger.info("Flow p2p filter processor component configuration received")
                restartFlowMapperService(event)
            }
            is StopEvent -> {
                logger.info("Stopping flow p2p filter component.")
                durableSub?.close()
                durableSub = null
                registration?.close()
                registration = null
            }
        }
    }

    /**
     * Recreate the Flow P2P Filter service in response to new config [event]
     */
    private fun restartFlowMapperService(event: ConfigChangedEvent) {
        val messagingConfig = event.config.toMessagingConfig()

        durableSub?.close()

        durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig(CONSUMER_GROUP, P2P_IN_TOPIC, messagingConfig.getInt(INSTANCE_ID)),
            FlowP2PFilterProcessor(cordaAvroSerializationFactory),
            messagingConfig,
            null
        )

        durableSub?.start()
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    override fun close() {
        coordinator.close()
    }
}
