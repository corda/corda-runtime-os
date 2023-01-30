package net.corda.virtualnode.write.db.impl.writer

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
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
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.util.debug
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.write.db.VirtualNodeInfoWriteService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import net.corda.data.identity.HoldingIdentity as HoldingIdentityAvro
import net.corda.data.virtualnode.VirtualNodeInfo as VirtualNodeInfoAvro

/**
 * Virtual Node Info Service writer so that we can [put] and [remove] info.
 */
@Suppress("UNUSED")
@Component(service = [VirtualNodeInfoWriteService::class])
class VirtualNodeInfoWriterComponentImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : VirtualNodeInfoWriteService {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        internal const val CLIENT_ID = "VIRTUAL_NODE_INFO_WRITER"
    }

    override val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<VirtualNodeInfoWriteService>()
    private val coordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName, ::processEvent)

    private var publisher: Publisher? = null
    private var registration: RegistrationHandle? = null
    private var configSubscription: AutoCloseable? = null

    override fun put(recordKey: HoldingIdentity, recordValue: VirtualNodeInfo) =
        publish(
            listOf(
                Record(
                    Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC,
                    recordKey.toAvro(),
                    recordValue.toAvro()
                )
            )
        )

    override fun remove(recordKey: HoldingIdentity) =
        publish(listOf(Record(Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC, recordKey.toAvro(), null)))

    /** Synchronous publish */
    @Suppress("ForbiddenComment")
    private fun publish(records: List<Record<HoldingIdentityAvro, VirtualNodeInfoAvro>>) {
        if (publisher == null) {
            log.error("Publisher is null, not publishing")
            return
        }

        //TODO:  according the publish kdoc, we need to handle failure, retries, and possibly transactions.  Next PR.
        val futures = publisher!!.publish(records)

        // Wait for the future (there should only be one) to complete.
        futures.forEach { it.get() }
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()


    /**
     * We received the following flow of events before the component is fully configured and
     * ready to publish:
     *
     *      onStart
     *      -> onRegistrationStatusChangeEvent
     *      -> onNewConfiguration
     *      -> onConfigChangedEvent
     */
    private fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event)
            is ConfigChangedEvent -> onConfigChangedEventReceived(coordinator, event)
            is StopEvent -> onStopEvent()
        }
    }

    private fun onStopEvent() {
        registration?.close()
        registration = null
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        configurationReadService.start()
        registration?.close()
        registration =
            coordinator.followStatusChangesByName(setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>()))
    }

    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent) {
        if (event.status == LifecycleStatus.UP) {
            configSubscription?.close()
            configSubscription = configurationReadService.registerComponentForUpdates(
                coordinator,
                setOf(ConfigKeys.MESSAGING_CONFIG)
            )
        } else {
            coordinator.updateStatus(event.status)
            configSubscription?.close()
            configSubscription = null
        }
    }

    /**
     * We only receive this event if the config contains the information we explicitly
     * require as defined in [onNewConfiguration]
     */
    private fun onConfigChangedEventReceived(coordinator: LifecycleCoordinator, event: ConfigChangedEvent) {
        log.debug { "Creating resources" }
        coordinator.updateStatus(LifecycleStatus.DOWN)
        createPublisher(event)
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun createPublisher(event: ConfigChangedEvent) {
        publisher?.close()
        publisher = publisherFactory.createPublisher(
            PublisherConfig(CLIENT_ID),
            event.config.getConfig(ConfigKeys.MESSAGING_CONFIG)
        )
    }

    @Deactivate
    fun close() {
        configSubscription?.close()
        registration?.close()
    }
}
