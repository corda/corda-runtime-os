package net.corda.testing.sandboxes.impl

import net.corda.testing.sandboxes.CpiLoaderService
import net.corda.testing.sandboxes.SandboxLoader
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.write.VirtualNodeInfoWriteService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("unused")
@Component
class SandboxLoaderImpl @Activate constructor(
    @Reference
    private val loader: CpiLoaderService,
    @Reference
    private val vnodeInfoService: VirtualNodeInfoWriteService
) : SandboxLoader {
    override fun loadCPI(resourceName: String, holdingIdentity: HoldingIdentity): VirtualNodeInfo {
        val cpi = loader.loadCPI(resourceName)
        return VirtualNodeInfo(holdingIdentity, cpi.metadata.id).also { vnodeInfo ->
            vnodeInfoService.put(vnodeInfo)
        }
    }

    override fun unloadCPI(virtualNodeInfo: VirtualNodeInfo) {
        vnodeInfoService.remove(virtualNodeInfo)
        val cpi = loader.get(virtualNodeInfo.cpiIdentifier).get()
            ?: throw IllegalStateException("No such CPI ${virtualNodeInfo.cpiIdentifier}")
        loader.unloadCPI(cpi)
    }
}
