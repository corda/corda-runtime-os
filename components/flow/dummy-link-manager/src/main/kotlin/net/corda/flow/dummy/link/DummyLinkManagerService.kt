package net.corda.flow.dummy.link

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
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
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [DummyLinkManagerService::class], immediate = true)
class DummyLinkManagerService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory
) : Lifecycle {

    private companion object {
        private val logger = contextLogger()
        private const val CONSUMER_GROUP = "DummyLinkManagerConsumer"
    }

    private val coordinator = coordinatorFactory.createCoordinator<DummyLinkManagerService>(::eventHandler)
    private var registration: RegistrationHandle? = null
    private var configHandle: AutoCloseable? = null
    private var durableSub: Subscription<String, AppMessage>? = null

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                logger.info("Starting dummy link manager component.")
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
                        setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG)
                    )
                } else {
                    configHandle?.close()
                }
            }
            is ConfigChangedEvent -> {
                logger.info("Dummy link manager component configuration received")
                restartFlowMapperService(event)
            }
            is StopEvent -> {
                logger.info("Stopping dummy link manager component.")
                durableSub?.close()
                durableSub = null
                registration?.close()
                registration = null
            }
        }
    }

    private fun restartFlowMapperService(event: ConfigChangedEvent) {
        val messagingConfig = event.config.toMessagingConfig()

        durableSub?.close()

        durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig(CONSUMER_GROUP, Schemas.P2P.P2P_OUT_TOPIC),
            DummyLinkManagerProcessor(),
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