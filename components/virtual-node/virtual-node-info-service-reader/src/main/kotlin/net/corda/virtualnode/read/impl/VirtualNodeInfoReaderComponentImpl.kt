package net.corda.virtualnode.read.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.common.ConfigChangedEvent
import net.corda.virtualnode.impl.VirtualNodeInfoProcessor
import net.corda.virtualnode.read.VirtualNodeInfoReaderComponent
import net.corda.virtualnode.service.VirtualNodeInfoListener
import net.corda.virtualnode.service.VirtualNodeInfoReader
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

/**
 * Virtual Node Info Service Component which implements [VirtualNodeInfoReader]
 *
 * Split into an event handler, a message processor, and this class, which contains the [LifecycleCoordinator]
 * for this component.
 */
@Component(service = [VirtualNodeInfoReaderComponent::class])
class VirtualNodeInfoReaderComponentImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    subscriptionFactory: SubscriptionFactory
) : VirtualNodeInfoReaderComponent {
    companion object {
        val log: Logger = contextLogger()
    }

    // This eventually needs to be passed in to here from the parent `main`
    private val instanceId: Int? = null

    /** The processor calls the call back when it receives a snapshot. */
    private val virtualNodeInfoProcessor: VirtualNodeInfoProcessor =
        VirtualNodeInfoProcessor(::setStatusToUp, ::setStatusToError)

    /**
     * The event handler needs to call back to here and use this coordinator, we do NOT want to pass around
     * the coordinator.
     */
    private val eventHandler: VirtualNodeInfoReaderEventHandler = VirtualNodeInfoReaderEventHandler(
        configurationReadService,
        virtualNodeInfoProcessor,
        subscriptionFactory,
        instanceId,
        this::onConfigChangeEvent
    )

    private val coordinator = coordinatorFactory.createCoordinator<VirtualNodeInfoReader>(eventHandler)

    /** Post a [ConfigChangedEvent]  */
    private fun onConfigChangeEvent(event: ConfigChangedEvent) = coordinator.postEvent(event)

    /** The processor calls this method on snapshot, and it updates the status of the coordinator. */
    private fun setStatusToUp() = coordinator.updateStatus(LifecycleStatus.UP)

    /** The processor calls this method on snapshot, and it updates the status of the coordinator. */
    private fun setStatusToError() = coordinator.updateStatus(LifecycleStatus.ERROR)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        log.debug { "Virtual Node Info Service component starting" }
        coordinator.start()
    }

    override fun stop() {
        log.debug { "Virtual Node Info Service component stopping" }
        coordinator.stop()
    }

    override fun close() {
        log.debug { "Virtual Node Info Service component closing" }
        coordinator.close()
        virtualNodeInfoProcessor.close()
    }

    override fun get(holdingIdentity: HoldingIdentity): VirtualNodeInfo? = virtualNodeInfoProcessor.get(holdingIdentity)

    override fun getById(id: String): VirtualNodeInfo? = virtualNodeInfoProcessor.getById(id)

    override fun registerCallback(listener: VirtualNodeInfoListener): AutoCloseable =
        virtualNodeInfoProcessor.registerCallback(listener)
}
