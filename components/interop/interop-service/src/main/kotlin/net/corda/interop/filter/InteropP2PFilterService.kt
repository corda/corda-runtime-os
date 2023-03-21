package net.corda.interop.filter

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.P2P.P2P_IN_TOPIC
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference

@Component(service = [InteropP2PFilterService::class], immediate = true)
class InteropP2PFilterService @Activate constructor(
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
        private const val CONSUMER_GROUP = "InteropFilterConsumer"
        private const val SUBSCRIPTION = "SUBSCRIPTION"
        private const val REGISTRATION = "REGISTRATION"
        private const val CONFIG_HANDLE = "CONFIG_HANDLE"
    }

    private val coordinator = coordinatorFactory.createCoordinator<InteropP2PFilterService>(::eventHandler)

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                coordinator.createManagedResource(REGISTRATION) {
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                        )
                    )
                }
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    coordinator.createManagedResource(CONFIG_HANDLE) {
                        configurationReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(MESSAGING_CONFIG)
                        )
                    }
                } else {
                    coordinator.closeManagedResources(setOf(CONFIG_HANDLE))
                }
            }
            is ConfigChangedEvent -> {
                restartFlowP2PFilterService(event)
            }
        }
    }

    /**
     * Recreate the Flow P2P Filter service in response to new config [event]
     */
    private fun restartFlowP2PFilterService(event: ConfigChangedEvent) {
        val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)

        coordinator.createManagedResource(SUBSCRIPTION) {
            subscriptionFactory.createDurableSubscription(
                SubscriptionConfig(CONSUMER_GROUP, P2P_IN_TOPIC),
                InteropP2PFilterProcessor(),
                messagingConfig,
                null
            ).also {
                it.start()
            }
        }

        coordinator.updateStatus(LifecycleStatus.UP)
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    @Suppress("unused")
    @Deactivate
    fun close() {
        coordinator.close()
    }
}
