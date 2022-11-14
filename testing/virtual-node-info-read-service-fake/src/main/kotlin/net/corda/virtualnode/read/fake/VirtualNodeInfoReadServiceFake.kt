package net.corda.virtualnode.read.fake

import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.reconciliation.VersionedRecord
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoListener
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import java.io.File
import java.util.stream.Stream


@ServiceRanking(Int.MAX_VALUE)
@Component(service = [VirtualNodeInfoReadService::class, VirtualNodeInfoReadServiceFake::class])
class VirtualNodeInfoReadServiceFake internal constructor(
    virtualNodeInfos: Map<HoldingIdentity, VirtualNodeInfo>,
    callbacks: List<VirtualNodeInfoListener>,
    coordinatorFactory: LifecycleCoordinatorFactory,
) : VirtualNodeInfoReadService {

    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
    ) : this(emptyMap(), emptyList(), coordinatorFactory)

    companion object {
        val logger = contextLogger()

        private val file = File("virtual-node-info-read-service-fake.yaml")
    }

    private val map: MutableMap<HoldingIdentity, VirtualNodeInfo> = virtualNodeInfos.toMutableMap()
    private val callbacks: MutableList<VirtualNodeInfoListener> = callbacks.toMutableList()

    init {
        map += VirtualNodeInfoReadServiceFakeParser.loadFrom(file).associateBy { it.holdingIdentity }
    }

    private val coordinator = coordinatorFactory.createCoordinator<VirtualNodeInfoReadService> { ev, c ->
        when (ev) {
            is StartEvent -> {
                logger.info("StartEvent received.")
                c.updateStatus(LifecycleStatus.UP)
            }
            is StopEvent -> {
                logger.info("StopEvent received.")
                c.updateStatus(LifecycleStatus.DOWN)
            }
            is ErrorEvent -> {
                logger.info("ErrorEvent ${ev.cause.message}")
                c.updateStatus(LifecycleStatus.ERROR)
            }
            is RegistrationStatusChangeEvent -> {
                updateCoordinatorStatus(ev.status)
            }
            else -> logger.info("Other event received $ev")
        }
    }

    private fun updateCoordinatorStatus(status: LifecycleStatus) {
        logger.info("Update coordinator status $status")
        if (status in listOf(LifecycleStatus.UP, LifecycleStatus.DOWN))
            coordinator.updateStatus(status)
    }

    /**
     * Adds a new [VirtualNodeInfo] and calls all registered callbacks.
     */
    fun addOrUpdate(virtualNodeInfo: VirtualNodeInfo) {
        map[virtualNodeInfo.holdingIdentity] = virtualNodeInfo
        callbacks.forEach { it.onUpdate(setOf(virtualNodeInfo.holdingIdentity), map) }
    }

    /**
     * Removes a [VirtualNodeInfo] and calls all registered callbacks.
     */
    fun remove(holdingIdentity: HoldingIdentity) {
        map.remove(holdingIdentity)
        val set = setOf(holdingIdentity)
        callbacks.forEach { it.onUpdate(set, map) }
    }

    /**
     * Clears all internal state of the service.
     */
    fun reset() {
        map.clear()
        callbacks.clear()
    }

    /**
     * Active wait the service is *running*.
     */
    fun waitUntilRunning() {
        repeat(10) {
            if (isRunning) return
            Thread.sleep(100)
        }
        check(false) { "Timeout waiting for ${this::class.simpleName} to start" }
    }

    override fun getAll(): List<VirtualNodeInfo> {
        throwIfNotRunning()
        return map.values.toList()
    }

    override fun get(holdingIdentity: HoldingIdentity): VirtualNodeInfo? {
        throwIfNotRunning()
        return map[holdingIdentity]
    }

    override fun getByHoldingIdentityShortHash(holdingIdentityShortHash: ShortHash): VirtualNodeInfo? {
        throwIfNotRunning()
        return map.entries.firstOrNull { holdingIdentityShortHash == it.key.shortHash }?.value
    }

    override fun registerCallback(listener: VirtualNodeInfoListener): AutoCloseable {
        throwIfNotRunning()
        callbacks.add(listener)
        listener.onUpdate(map.keys, map)
        return AutoCloseable { callbacks.remove(listener) }
    }

    override fun getAllVersionedRecords(): Stream<VersionedRecord<HoldingIdentity, VirtualNodeInfo>>? {
        throw CordaRuntimeException("Not yet implemented")
    }

    override val lifecycleCoordinatorName: LifecycleCoordinatorName
        get() = coordinator.name

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

    private fun throwIfNotRunning() {
        val reallyRunning = isRunning
        check(reallyRunning) { "${this::class.simpleName} has not been started." }
    }
}
