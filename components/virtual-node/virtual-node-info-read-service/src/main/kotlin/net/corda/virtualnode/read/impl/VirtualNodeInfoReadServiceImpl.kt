package net.corda.virtualnode.read.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.core.ShortHash
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.reconciliation.VersionedRecord
import net.corda.v5.base.util.debug
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoListener
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.stream.Stream

/**
 * Virtual Node Info Service Component which implements [VirtualNodeInfoReadService]
 *
 * Split into an event handler, a message processor, and this class, which contains the [LifecycleCoordinator]
 * for this component.
 */
@Suppress("Unused")
@Component(service = [VirtualNodeInfoReadService::class])
class VirtualNodeInfoReadServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    subscriptionFactory: SubscriptionFactory
) : VirtualNodeInfoReadService {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
        subscriptionFactory
    )

    private val coordinator = coordinatorFactory.createCoordinator<VirtualNodeInfoReadService>(eventHandler)

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

    @Deactivate
    fun close() {
        log.debug { "Virtual Node Info Service component closing" }
        coordinator.close()
        virtualNodeInfoProcessor.close()
    }

    override fun getAll(): List<VirtualNodeInfo> = virtualNodeInfoProcessor.getAll()

    override fun get(holdingIdentity: HoldingIdentity): VirtualNodeInfo? = virtualNodeInfoProcessor.get(holdingIdentity)

    override fun getByHoldingIdentityShortHash(holdingIdentityShortHash: ShortHash): VirtualNodeInfo?
        = virtualNodeInfoProcessor.getById(holdingIdentityShortHash)

    override fun registerCallback(listener: VirtualNodeInfoListener): AutoCloseable =
        virtualNodeInfoProcessor.registerCallback(listener)

    override fun getAllVersionedRecords(): Stream<VersionedRecord<HoldingIdentity, VirtualNodeInfo>>? =
        getAll()
            .stream()
            .map {
                object : VersionedRecord<HoldingIdentity, VirtualNodeInfo> {
                    override val version = it.version
                    override val isDeleted = false
                    override val key = it.holdingIdentity
                    override val value = it
                }
            }

    override val lifecycleCoordinatorName: LifecycleCoordinatorName
        get() = coordinator.name
}
