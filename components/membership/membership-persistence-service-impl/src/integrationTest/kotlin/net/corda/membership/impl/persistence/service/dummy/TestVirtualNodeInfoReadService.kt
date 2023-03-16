package net.corda.membership.impl.persistence.service.dummy

import net.corda.crypto.core.ShortHash
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.reconciliation.VersionedRecord
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoListener
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream

interface TestVirtualNodeInfoReadService : VirtualNodeInfoReadService {
    fun putVNodeInfo(vnodeInfo: VirtualNodeInfo)
}

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [VirtualNodeInfoReadService::class, TestVirtualNodeInfoReadService::class])
class TestVirtualNodeInfoReadServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : TestVirtualNodeInfoReadService {

    companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private const val UNIMPLEMENTED_FUNCTION = "Called unimplemented function for test service"
    }

    private val coordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName) { event, coordinator ->
        if (event is StartEvent) {
            coordinator.updateStatus(LifecycleStatus.UP)
        }
    }
    override val isRunning: Boolean
        get() = coordinator.status == LifecycleStatus.UP

    private val vnodes: ConcurrentHashMap<HoldingIdentity, VirtualNodeInfo> = ConcurrentHashMap()

    override fun putVNodeInfo(vnodeInfo: VirtualNodeInfo) {
        vnodes[vnodeInfo.holdingIdentity] = vnodeInfo
    }

    override fun getAll() = vnodes.entries.map { it.value }

    override fun get(holdingIdentity: HoldingIdentity) = vnodes[holdingIdentity]

    override fun getByHoldingIdentityShortHash(holdingIdentityShortHash: ShortHash) = vnodes.entries.firstOrNull {
        it.key.shortHash == holdingIdentityShortHash
    }?.value

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
        get() = LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>()

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}
