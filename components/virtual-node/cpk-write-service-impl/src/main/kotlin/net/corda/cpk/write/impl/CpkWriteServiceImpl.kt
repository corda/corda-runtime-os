package net.corda.cpk.write.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpk.readwrite.CpkServiceConfigKeys
import net.corda.cpk.write.CpkWriteService
import net.corda.cpk.write.impl.services.kafka.CpkChecksumCache
import net.corda.cpk.write.impl.services.kafka.impl.CpkChecksumCacheImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.seconds
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.time.Duration

// something that will read from the database
// a cache
// something that publishes to Kafka
@Suppress("Warnings", "Unused")
@Component(service = [CpkWriteService::class])
class CpkWriteServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : CpkWriteService, LifecycleEventHandler {
    companion object {
        val logger: Logger = contextLogger()
    }

    private val coordinator = coordinatorFactory.createCoordinator<CpkWriteService>(this)

    @VisibleForTesting
    internal var configReadServiceRegistration: RegistrationHandle? = null
    @VisibleForTesting
    internal var configSubscription: AutoCloseable? = null
    @VisibleForTesting
    internal var cpkChecksumCache: CpkChecksumCache? = null
    @VisibleForTesting
    internal var publisher: Publisher? = null

    //TODO: populate the following with configuration
    private val timeout: Duration = 20.seconds

    /**
     * Event loop
     */
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            is ConfigChangedEvent -> onConfigChangedEvent(event, coordinator)
            is StopEvent -> onStopEvent()
        }
    }

    /**
     * We depend on the [ConfigurationReadService] so we 'listen' to [RegistrationStatusChangeEvent]
     * to tell us when it is ready so we can register ourselves to handle config updates.
     */
    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        configReadServiceRegistration?.close()
        configReadServiceRegistration =
            coordinator.followStatusChangesByName(setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>()))
    }

    /**
     * If the thing(s) we depend on are up (only the [ConfigurationReadService]),
     * then register `this` for config updates
     */
    private fun onRegistrationStatusChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        if (event.status == LifecycleStatus.UP) {
            configSubscription = configReadService.registerComponentForUpdates(
                coordinator,
                setOf(ConfigKeys.BOOT_CONFIG)
            )
        } else {
            logger.warn(
                "Received a ${RegistrationStatusChangeEvent::class.java.simpleName} with status ${event.status}." +
                        " Component ${this::class.java.simpleName} is not started"
            )
            closeResources()
        }
    }

    /**
     * We've received a config event that we care about, we can now write cpks
     */
    private fun onConfigChangedEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        val config = event.config[ConfigKeys.BOOT_CONFIG]!!
        // TODO - fix configuration for cache and publisher
        if (config.hasPath("todo")) {

            cpkChecksumCache = CpkChecksumCacheImpl(
                subscriptionFactory,
                SubscriptionConfig("todo", "todo"),
                config
            )
            publisher = publisherFactory.createPublisher(
                PublisherConfig("todo"),
                config
            )
            coordinator.updateStatus(LifecycleStatus.UP)
        } else {
            logger.warn(
                "Need ${CpkServiceConfigKeys.CPK_CACHE_DIR} to be specified in the boot config." +
                        " Component ${this::class.java.simpleName} is not started"
            )
            closeResources()
        }
    }

    /**
     * Close the registration.
     */
    private fun onStopEvent() {
        closeResources()
    }

    override fun putAllCpk() {
    }

    override fun putMissingCpk() {

    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.debug { "Cpk Write Service starting" }
        cpkChecksumCache!!.start()
        coordinator.start()
    }

    override fun stop() {
        logger.debug { "Cpk Write Service stopping" }
        coordinator.stop()
        closeResources()
    }

    private fun closeResources() {
        configReadServiceRegistration?.close()
        configReadServiceRegistration = null
        configSubscription?.close()
        configSubscription = null
        cpkChecksumCache?.close()
        cpkChecksumCache = null
        publisher?.close()
        publisher = null
    }
}
