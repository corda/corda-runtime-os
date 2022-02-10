package net.corda.testing.sandboxes.impl

import java.util.concurrent.ConcurrentHashMap
import net.corda.v5.base.util.loggerFor
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoListener
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.write.VirtualNodeInfoWriteService
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

interface VirtualNodeInfoService  : VirtualNodeInfoReadService, VirtualNodeInfoWriteService

@Suppress("unused")
@Component(service = [
    VirtualNodeInfoService::class,
    VirtualNodeInfoReadService::class,
    VirtualNodeInfoWriteService::class
])
@ServiceRanking(Int.MAX_VALUE)
class VirtualNodeInfoServiceImpl : VirtualNodeInfoService {
    private val logger = loggerFor<VirtualNodeInfoService>()
    private val virtualNodeInfoMap = ConcurrentHashMap<HoldingIdentity, VirtualNodeInfo>()

    override val isRunning: Boolean
        get() = true

    override fun get(holdingIdentity: HoldingIdentity): VirtualNodeInfo? {
        return virtualNodeInfoMap[holdingIdentity]
    }

    override fun getById(id: String): VirtualNodeInfo? {
        TODO("Not yet implemented - getById")
    }

    override fun put(virtualNodeInfo: VirtualNodeInfo) {
        if (virtualNodeInfoMap.putIfAbsent(virtualNodeInfo.holdingIdentity,  virtualNodeInfo) != null) {
            throw IllegalStateException("Virtual node $virtualNodeInfo already exists.")
        }
    }

    override fun remove(virtualNodeInfo: VirtualNodeInfo) {
        virtualNodeInfoMap.remove(virtualNodeInfo.holdingIdentity)
    }

    override fun registerCallback(listener: VirtualNodeInfoListener): AutoCloseable {
        return AutoCloseable {}
    }

    override fun start() {
        logger.info("Started")
    }

    override fun stop() {
        logger.info("Stopped")
    }
}
