package net.corda.virtualnode.impl

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
import net.corda.virtualnode.VirtualNodeInfoListener
import net.corda.virtualnode.VirtualNodeInfoService
import net.corda.virtualnode.VirtualNodeInfoServiceComponent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

/**
 * Virtual Node Info Service Component which implements [VirtualNodeInfoService]
 *
 * Split into an event handler, a message processor, and this class, which contains the [LifecycleCoordinator]
 * for this component.
 */
@Component(service = [VirtualNodeInfoServiceComponent::class])
class VirtualNodeInfoServiceComponentImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    subscriptionFactory: SubscriptionFactory
) : VirtualNodeInfoServiceComponent {
    companion object {
        val log: Logger = contextLogger()
    }

    /** The processor calls the call back when it receives a snapshot. */
    private val virtualNodeInfoProcessor: VirtualNodeInfoProcessor = VirtualNodeInfoProcessor(::setStatusToUp, ::setStatusToError)

    /**
     * The event handler needs to call back to here and use this coordinator, we do NOT want to pass around
     * the coordinator.
     */
    private val eventHandler: VirtualNodeInfoEventHandler = VirtualNodeInfoEventHandler(
        configurationReadService,
        virtualNodeInfoProcessor,
        subscriptionFactory,
        this::onConfigChangeEvent
    )

    private val coordinator = coordinatorFactory.createCoordinator<VirtualNodeInfoService>(eventHandler)

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
