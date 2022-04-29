package net.corda.flow.testing.fakes

import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoListener
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

@ServiceRanking(Int.MAX_VALUE)
@Component(
    service = [VirtualNodeInfoReadService::class, FakeVirtualNodeInfoReadService::class]
)
class FakeVirtualNodeInfoReadService : VirtualNodeInfoReadService {

    private val vNodes = mutableMapOf<HoldingIdentity, VirtualNodeInfo>()

    fun addVirtualNodeInfo(holdingIdentity: HoldingIdentity, virtualNodeInfo: VirtualNodeInfo) {
        vNodes[holdingIdentity] = virtualNodeInfo
    }

    fun reset(){
        vNodes.clear()
    }

    override fun getAll(): List<VirtualNodeInfo> {
        TODO("Not yet implemented")
    }

    override fun get(holdingIdentity: HoldingIdentity): VirtualNodeInfo? {
        return vNodes[holdingIdentity]
    }

    override fun getById(id: String): VirtualNodeInfo? {
        TODO("Not yet implemented")
    }

    override fun registerCallback(listener: VirtualNodeInfoListener): AutoCloseable {
        TODO("Not yet implemented")
    }

    override val isRunning: Boolean
        get() = true

    override fun start() {
    }

    override fun stop() {
    }
}

