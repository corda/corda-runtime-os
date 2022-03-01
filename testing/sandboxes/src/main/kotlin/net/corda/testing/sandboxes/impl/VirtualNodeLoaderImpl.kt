package net.corda.testing.sandboxes.impl

import net.corda.libs.packaging.CpiIdentifier
import java.util.concurrent.ConcurrentHashMap
import net.corda.packaging.CPI
import net.corda.testing.sandboxes.CpiLoader
import net.corda.testing.sandboxes.VirtualNodeLoader
import net.corda.v5.base.util.loggerFor
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoListener
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking

@Suppress("unused")
@Component(service = [ VirtualNodeLoader::class, VirtualNodeInfoReadService::class ])
@ServiceRanking(Int.MAX_VALUE)
class VirtualNodeLoaderImpl @Activate constructor(
    @Reference
    private val cpiLoader: CpiLoader,
) : VirtualNodeLoader, VirtualNodeInfoReadService {
    private val virtualNodeInfoMap = ConcurrentHashMap<HoldingIdentity, VirtualNodeInfo>()
    private val resourcesLookup = mutableMapOf<CpiIdentifier, String>()
    private val cpiResources = mutableMapOf<String, CPI>()
    private val logger = loggerFor<VirtualNodeLoader>()

    override val isRunning: Boolean
        get() = true

    override fun loadVirtualNode(resourceName: String, holdingIdentity: HoldingIdentity): VirtualNodeInfo {
        // TODO - refactor this when CPI loader code moves from api to runtime-os
        val cpi = cpiResources.computeIfAbsent(resourceName) { key ->
            cpiLoader.loadCPI(key).also { cpi ->
                resourcesLookup[CpiIdentifier.fromLegacy(cpi.metadata.id)] = key
            }
        }
        return VirtualNodeInfo(holdingIdentity, CpiIdentifier(
            cpi.metadata.id.name,
            cpi.metadata.id.version,
            cpi.metadata.id.signerSummaryHash
        )).also(::put)
    }

    override fun unloadVirtualNode(virtualNodeInfo: VirtualNodeInfo) {
        virtualNodeInfoMap.remove(virtualNodeInfo.holdingIdentity)
    }

    override fun forgetCPI(id: CpiIdentifier) {
        resourcesLookup.remove(id)?.also(cpiResources::remove)
    }

    override fun get(holdingIdentity: HoldingIdentity): VirtualNodeInfo? {
        return virtualNodeInfoMap[holdingIdentity]
    }

    override fun getById(id: String): VirtualNodeInfo? {
        TODO("Not yet implemented - getById")
    }

    private fun put(virtualNodeInfo: VirtualNodeInfo) {
        if (virtualNodeInfoMap.putIfAbsent(virtualNodeInfo.holdingIdentity, virtualNodeInfo) != null) {
            throw IllegalStateException("Virtual node $virtualNodeInfo already exists.")
        }
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
