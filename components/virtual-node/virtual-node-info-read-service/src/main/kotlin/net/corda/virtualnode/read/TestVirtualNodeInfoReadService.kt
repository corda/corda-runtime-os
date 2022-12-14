package net.corda.virtualnode.read

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.reconciliation.VersionedRecord
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.VirtualNodeInfo
import java.util.stream.Stream

class TestVirtualNodeInfoReadService(
    coordinatorFactory: LifecycleCoordinatorFactory
) : VirtualNodeInfoReadService {

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<VirtualNodeInfoReadService>{ event, coordinator ->
        if(event is StartEvent) { coordinator.updateStatus(LifecycleStatus.UP) }
    }

    companion object {
        val logger = contextLogger()
        private const val UNIMPLEMENTED_FUNCTION = "Called unimplemented function for test service"
    }

    override fun getAll(): List<VirtualNodeInfo> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun get(holdingIdentity: HoldingIdentity): VirtualNodeInfo? {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun getByHoldingIdentityShortHash(holdingIdentityShortHash: ShortHash): VirtualNodeInfo? {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun registerCallback(listener: VirtualNodeInfoListener): AutoCloseable {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun getAllVersionedRecords(): Stream<VersionedRecord<HoldingIdentity, VirtualNodeInfo>>? {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override val lifecycleCoordinatorName: LifecycleCoordinatorName
        get() = lifecycleCoordinator.name

    override val isRunning: Boolean
        get() =  lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }
}