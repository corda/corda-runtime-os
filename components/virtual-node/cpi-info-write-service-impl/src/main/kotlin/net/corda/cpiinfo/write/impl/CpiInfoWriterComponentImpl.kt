package net.corda.cpiinfo.write.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
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
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.VirtualNode.Companion.CPI_INFO_TOPIC
import net.corda.schema.configuration.ConfigKeys
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import net.corda.data.packaging.CpiIdentifier as CpiIdentifierAvro
import net.corda.data.packaging.CpiMetadata as CpiMetadataAvro

/**
 * CPI Info Service writer so that we can [put] and [remove]
 * [CpiMetadata] from Kafka compacted queues.
 */
@Suppress("Unused")
@Component(service = [CpiInfoWriteService::class])
class CpiInfoWriterComponentImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : CpiInfoWriteService, LifecycleEventHandler {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        internal const val CLIENT_ID = "CPI_INFO_WRITER"
    }

    override val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<CpiInfoWriteService>()

    private val coordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName, this)
    private var publisher: Publisher? = null
    private var registration: RegistrationHandle? = null
    private var configSubscription: AutoCloseable? = null

    override fun put(cpiIdentifier: CpiIdentifier, cpiMetadata: CpiMetadata) =
        publish(listOf(Record(CPI_INFO_TOPIC, cpiIdentifier.toAvro(), cpiMetadata.toAvro())))

    override fun remove(cpiIdentifier: CpiIdentifier) =
        publish(listOf(Record(CPI_INFO_TOPIC, cpiIdentifier.toAvro(), null)))

    /** Synchronous publish */
    private fun publish(records: List<Record<CpiIdentifierAvro, CpiMetadataAvro>>) {
        if (publisher == null) {
            log.error("Cpi Info Writer publisher is null, not publishing, this error will addressed in a later PR")
            return
        }

        val futures = publisher!!.publish(records)

        // Wait for the future (there should only be one) to complete.
        futures.forEach { it.get() }
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event)
            is ConfigChangedEvent -> onConfigChangedEvent(coordinator, event)
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
        registration?.close()
        registration = null

        configSubscription?.close()
        configSubscription = null

        publisher?.close()
        publisher = null
    }

    private fun onConfigChangedEvent(coordinator: LifecycleCoordinator, event: ConfigChangedEvent) {
        val config = event.config[ConfigKeys.MESSAGING_CONFIG] ?: return
        coordinator.updateStatus(LifecycleStatus.DOWN)

        publisher?.close()
        publisher = publisherFactory.createPublisher(PublisherConfig(CLIENT_ID), config)
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent) {
        if (event.status == LifecycleStatus.UP) {
            configSubscription?.close()
            configSubscription =
                configurationReadService.registerComponentForUpdates(coordinator, setOf(ConfigKeys.MESSAGING_CONFIG))
        } else {
            coordinator.updateStatus(event.status)
            configSubscription?.close()
            configSubscription = null
            publisher?.close()
            publisher = null
        }
    }
}
