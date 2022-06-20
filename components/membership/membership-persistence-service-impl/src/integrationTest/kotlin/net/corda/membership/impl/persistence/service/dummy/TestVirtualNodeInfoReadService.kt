package net.corda.membership.impl.persistence.service.dummy

import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.reconciliation.VersionedRecord
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoListener
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream

interface TestVirtualNodeInfoReadService : VirtualNodeInfoReadService {
    fun putVNodeInfo(vnodeInfo: VirtualNodeInfo)
}

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [VirtualNodeInfoReadService::class, TestVirtualNodeInfoReadService::class])
class TestVirtualNodeInfoReadServiceImpl : TestVirtualNodeInfoReadService {

    companion object {
        val logger = contextLogger()

        private const val UNIMPLEMENTED_FUNCTION = "Called unimplemented function for test service"
    }
    override var isRunning: Boolean = true

    val vnodes: ConcurrentHashMap<HoldingIdentity, VirtualNodeInfo> = ConcurrentHashMap()

    override fun putVNodeInfo(vnodeInfo: VirtualNodeInfo) {
        vnodes[vnodeInfo.holdingIdentity] = vnodeInfo
    }

    override fun getAll() = vnodes.entries.map { it.value }

    override fun get(holdingIdentity: HoldingIdentity) = vnodes[holdingIdentity]

    override fun getById(id: String) = vnodes.entries.firstOrNull {
        it.key.id == id
    }?.value

    override fun registerCallback(listener: VirtualNodeInfoListener): AutoCloseable {
        with(UNIMPLEMENTED_FUNCTION){
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun getAllVersionedRecords(): Stream<VersionedRecord<HoldingIdentity, VirtualNodeInfo>>? {
        with(UNIMPLEMENTED_FUNCTION){
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override val lifecycleCoordinatorName: LifecycleCoordinatorName
        get() = LifecycleCoordinatorName.forComponent<TestVirtualNodeInfoReadServiceImpl>()

    override fun start() {
        isRunning = true
    }

    override fun stop() {
        isRunning = false
    }
}