package net.corda.interop

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.interop.service.InteropMemberRegistrationService
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
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
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
    private val publisherFactory: PublisherFactory,
    @Reference(service = InteropMemberRegistrationService::class)
    private val registrationService: InteropMemberRegistrationService
) : Lifecycle {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val CONSUMER_GROUP = "InteropConsumer"
        private const val SUBSCRIPTION = "SUBSCRIPTION"
        private const val REGISTRATION = "REGISTRATION"
        private const val CONFIG_HANDLE = "CONFIG_HANDLE"
    }

    private val coordinator = coordinatorFactory.createCoordinator<InteropService>(::eventHandler)
    private var publisher: Publisher? = null

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("$event")
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
                restartInteropProcessor(event)
            }
        }
    }

    private fun restartInteropProcessor(event: ConfigChangedEvent) {
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
        publisher?.close()
        publisher = publisherFactory.createPublisher(
            PublisherConfig("interop-registration-service"),
            event.config.getConfig(MESSAGING_CONFIG)
        )
        publisher?.start()
        logger.info("Publishing member infos")
        publisher?.publish(registrationService.createDummyMemberInfo())
        logger.info("Publishing hosted identities")
        publisher?.publish(registrationService.createDummyHostedIdentity())
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    override val isRunning: Boolean
        get() {
            logger.info("isRunning=${coordinator.isRunning}")
            return coordinator.isRunning
        }

    override fun start() {
        logger.info("starting")
        coordinator.start()
    }

    override fun stop() {
        logger.info("stopping")
        coordinator.stop()
    }

    @Suppress("unused")
    @Deactivate
    fun close() {
        logger.info("closing")
        coordinator.close()
    }
}
