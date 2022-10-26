package net.corda.membership.impl.registration.dummy

import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.reconciliation.VersionedRecord
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoListener
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking
import java.util.stream.Stream

interface TestVirtualNodeInfoReadService : VirtualNodeInfoReadService {
    fun setTestVirtualNodeInfo(virtualNodeInfo: VirtualNodeInfo)
}

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [VirtualNodeInfoReadService::class, TestVirtualNodeInfoReadService::class])
internal class TestVirtualNodeInfoReadServiceImpl @Activate constructor() : TestVirtualNodeInfoReadService {

    private val testVirtualNodeInfoList = mutableListOf<VirtualNodeInfo>()

    override fun setTestVirtualNodeInfo(virtualNodeInfo: VirtualNodeInfo) {
        testVirtualNodeInfoList.add(virtualNodeInfo)
    }

    override fun getAll(): List<VirtualNodeInfo> = testVirtualNodeInfoList

    override fun get(holdingIdentity: HoldingIdentity): VirtualNodeInfo? {
        return testVirtualNodeInfoList.firstOrNull { it.holdingIdentity ==  holdingIdentity }
    }

    override fun getByHoldingIdentityShortHash(holdingIdentityShortHash: ShortHash): VirtualNodeInfo? {
        return testVirtualNodeInfoList.firstOrNull { it.holdingIdentity.shortHash ==  holdingIdentityShortHash }
    }

    override fun registerCallback(listener: VirtualNodeInfoListener): AutoCloseable {
        return AutoCloseable { return@AutoCloseable }
    }

    override fun getAllVersionedRecords(): Stream<VersionedRecord<HoldingIdentity, VirtualNodeInfo>>? {
        return null
    }

    override val lifecycleCoordinatorName: LifecycleCoordinatorName
        get() = LifecycleCoordinatorName.forComponent<TestVirtualNodeInfoReadServiceImpl>()

    override val isRunning: Boolean
        get() = true

    override fun start() {
        return
    }

    override fun stop() {
        return
    }

}