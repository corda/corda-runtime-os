package net.corda.interop

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.flow.event.SessionEvent
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
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.ConfigKeys.P2P_LINK_MANAGER_CONFIG
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("LongParameterList", "Unused")
@Component(service = [InteropService::class], immediate = true)
class InteropService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : Lifecycle {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val CONSUMER_GROUP = "InteropConsumer"
        private const val SUBSCRIPTION = "SUBSCRIPTION"
        private const val REGISTRATION = "REGISTRATION"
        private const val CONFIG_HANDLE = "CONFIG_HANDLE"
    }

    private val coordinator = coordinatorFactory.createCoordinator<InteropService>(::eventHandler)
    private val sessionEventSerializer = cordaAvroSerializationFactory.createAvroSerializer<SessionEvent> { }
    private var publisher: Publisher? = null

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                configurationReadService.start()
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
                            setOf(MESSAGING_CONFIG, P2P_LINK_MANAGER_CONFIG)
                        )
                    }
                } else {
                    coordinator.closeManagedResources(setOf(CONFIG_HANDLE))
                    coordinator.updateStatus(LifecycleStatus.DOWN, "Dependency ${coordinator.name} is DOWN")
                }
            }
            is ConfigChangedEvent -> {
                restartInteropProcessor(event)
            }
        }
    }

    private fun restartInteropProcessor(event: ConfigChangedEvent) {
        logger.info("restartInteropProcessor $event")
        val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
        coordinator.createManagedResource(SUBSCRIPTION) {
            subscriptionFactory.createDurableSubscription(
                SubscriptionConfig(CONSUMER_GROUP, P2P_IN_TOPIC),
                InteropProcessor(cordaAvroSerializationFactory),
                messagingConfig,
                null
            ).also {
                it.start()
            }
        }
        //TODO below is temporary tactical code to setup members of interop group,
        // this will be phased out later on by CORE-10446
//        publisher?.close()
//        publisher = publisherFactory.createPublisher(
//            PublisherConfig("interop-registration-service"),
//            event.config.getConfig(MESSAGING_CONFIG)
//        )
//        publisher?.start()
        logger.info("restartInteropProcessor - end")
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    @Deactivate
    fun close() {
        coordinator.close()
    }
}
