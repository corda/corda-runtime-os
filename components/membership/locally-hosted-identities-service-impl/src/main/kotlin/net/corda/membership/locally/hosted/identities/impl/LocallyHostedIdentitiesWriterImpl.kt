package net.corda.membership.locally.hosted.identities.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.libs.configuration.helper.getConfig
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
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesWriter
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Locally-hosted Identities writer to [put] and [remove] records from Kafka.
 */
@Component(service = [LocallyHostedIdentitiesWriter::class])
class LocallyHostedIdentitiesWriterImpl@Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
) : LocallyHostedIdentitiesWriter {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val CLIENT_ID = "LOCALLY_HOSTED_IDENTITIES_WRITER"
    }

    private val coordinator = coordinatorFactory.createCoordinator<LocallyHostedIdentitiesWriter>(::processEvent)
    private val lock = ReentrantLock()
    private var publisher: Publisher? = null
    private var registration: RegistrationHandle? = null
    private var configSubscription: AutoCloseable? = null

    override fun put(recordKey: String, recordValue: HostedIdentityEntry) {
        // TODO change to trace
        logger.info("Reconciling hosted identity record for '${recordValue.holdingIdentity}', version=${recordValue.version}.")
        publish(
            listOf(
                Record(
                    Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC,
                    recordKey,
                    recordValue
                )
            )
        )
    }

    override fun remove(recordKey: String) {
        publish(
            listOf(
                Record(
                    Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC,
                    recordKey,
                    null
                )
            )
        )
    }

    private fun publish(records: List<Record<String, HostedIdentityEntry>>) {
        lock.withLock {
            publisher?.let {
                it.publish(records).forEach { future ->
                    future.get()
                }
            } ?: logger.error("Publisher is null, not publishing")
        }
    }

    override val lifecycleCoordinatorName: LifecycleCoordinatorName
        get() = coordinator.name

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()

    private fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event)
            is ConfigChangedEvent -> onConfigChangedEventReceived(coordinator, event)
            is StopEvent -> onStopEvent()
        }
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        configurationReadService.start()
        registration?.close()
        registration =
            coordinator.followStatusChangesByName(setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>()))
    }

    private fun onStopEvent() {
        lock.withLock {
            coordinator.updateStatus(LifecycleStatus.DOWN, "Received stop event.")
            registration?.close()
            registration = null

            configSubscription?.close()
            configSubscription = null

            publisher?.close()
            publisher = null
        }
    }

    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent) {
        if (event.status == LifecycleStatus.UP) {
            configSubscription?.close()
            configSubscription = configurationReadService.registerComponentForUpdates(
                coordinator,
                setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG)
            )
        } else {
            coordinator.updateStatus(event.status, "Received ${event.status} event.")
            configSubscription?.close()
            configSubscription = null
        }
    }

    private fun onConfigChangedEventReceived(coordinator: LifecycleCoordinator, event: ConfigChangedEvent) {
        logger.debug { "Creating resources" }
        coordinator.updateStatus(LifecycleStatus.DOWN)
        lock.withLock {
            publisher?.close()
            publisher = publisherFactory.createPublisher(
                PublisherConfig(CLIENT_ID),
                event.config.getConfig(ConfigKeys.MESSAGING_CONFIG)
            ).also { it.start() }
        }
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    @Deactivate
    fun close() {
        configSubscription?.close()
        registration?.close()
    }
}
