package net.corda.virtualnode.write.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.common.ConfigChangedEvent
import net.corda.virtualnode.common.MessagingConfigEventHandler
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.write.VirtualNodeInfoWriterComponent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Component(service = [VirtualNodeInfoWriterComponent::class])
class VirtualNodeInfoWriterComponentImpl @Activate constructor(
    @Reference
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : VirtualNodeInfoWriterComponent {
    companion object {
        val log: Logger = contextLogger()
        internal const val CLIENT_ID = "VIRTUAL_NODE_INFO_WRITER"
    }

    // This eventually needs to be passed in to here from the parent `main`
    private val instanceId: Int? = null

    private val eventHandler: MessagingConfigEventHandler =
        MessagingConfigEventHandler(configurationReadService, this::onConfigChangeEvent, this::onConfig)

    private val coordinator = coordinatorFactory.createCoordinator<VirtualNodeInfoWriterComponent>(eventHandler)
    private var publisher: Publisher? = null

    override fun put(virtualNodeInfo: VirtualNodeInfo) {
        publish(
            listOf(
                Record(
                    Schemas.VIRTUAL_NODE_INFO_TOPIC,
                    virtualNodeInfo.holdingIdentity.toAvro(),
                    virtualNodeInfo.toAvro()
                )
            )
        )
    }

    override fun remove(virtualNodeInfo: VirtualNodeInfo) {
        publish(
            listOf(
                Record(
                    Schemas.VIRTUAL_NODE_INFO_TOPIC,
                    virtualNodeInfo.holdingIdentity.toAvro(),
                    null
                )
            )
        )
    }

    /** Synchronous publish */
    @Suppress("ForbiddenComment")
    private fun publish(records: List<Record<HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>) {
        if (publisher == null) {
            log.error("Virtual Node Info Writer is null, not publishing, this error will addressed in a later PR")
            return
        }

        //TODO:  according the publish kdoc, we need to handle failure, retries, and possibly transactions.  Next PR.
        val futures = publisher!!.publish(records)

        // Wait for the future (there should only be one) to complete.
        futures.forEach { it.get() }
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        log.debug { "Virtual Node Info Reader Service component starting" }
        coordinator.start()
    }

    override fun stop() {
        log.debug { "Virtual Node Info Reader Service component stopping" }
        coordinator.stop()
    }

    /** Post a [ConfigChangedEvent]  */
    private fun onConfigChangeEvent(event: ConfigChangedEvent)  {
        coordinator.postEvent(event)
        coordinator.updateStatus(LifecycleStatus.DOWN)
    }

    /**
     * Once we finally get a config, we can create a publisher connected to the
     * correct Kafka instance, and flag that we're up.
     */
    private fun onConfig(coordinator: LifecycleCoordinator, config: SmartConfig) {
        publisher?.close()
        publisher = publisherFactory.createPublisher(PublisherConfig(CLIENT_ID, instanceId), config)
        coordinator.updateStatus(LifecycleStatus.UP)
    }
}
