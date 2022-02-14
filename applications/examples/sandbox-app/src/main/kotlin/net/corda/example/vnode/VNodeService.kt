package net.corda.example.vnode

import net.corda.flow.manager.FlowSandboxService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.testing.sandboxes.CpiLoaderService
import net.corda.v5.base.util.loggerFor
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.write.VirtualNodeInfoWriteService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

interface VNodeService {
    fun loadCPI(resourceName: String, holdingIdentity: HoldingIdentity): VirtualNodeInfo
    fun getOrCreateSandbox(holdingIdentity: HoldingIdentity): SandboxGroupContext
    fun unloadCPI(virtualNodeInfo: VirtualNodeInfo)
}

@Suppress("unused")
@Component(service = [ VNodeService::class ])
class VNodeServiceImpl @Activate constructor(
    @Reference
    private val flowSandboxService: FlowSandboxService,

    @Reference
    private val loaderService: CpiLoaderService,

    @Reference
    private val vnodeInfoWriter: VirtualNodeInfoWriteService
) : VNodeService {
    private val logger = loggerFor<VNodeService>()

    override fun loadCPI(resourceName: String, holdingIdentity: HoldingIdentity): VirtualNodeInfo {
        val cpi = loaderService.loadCPI(resourceName)
        return VirtualNodeInfo(holdingIdentity, cpi.metadata.id).also { vnodeInfo ->
            vnodeInfoWriter.put(vnodeInfo)
        }
    }

    override fun unloadCPI(virtualNodeInfo: VirtualNodeInfo) {
        vnodeInfoWriter.remove(virtualNodeInfo)
        val cpi = loaderService.get(virtualNodeInfo.cpiIdentifier).get()
            ?: throw IllegalStateException("No such CPI ${virtualNodeInfo.cpiIdentifier}")
        cpi.use(loaderService::unloadCPI)
    }

    override fun getOrCreateSandbox(holdingIdentity: HoldingIdentity): SandboxGroupContext {
        return flowSandboxService.get(holdingIdentity)
    }
}
