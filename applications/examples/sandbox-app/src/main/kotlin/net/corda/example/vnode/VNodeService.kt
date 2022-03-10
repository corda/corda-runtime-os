package net.corda.example.vnode

import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.testing.sandboxes.CpiLoader
import net.corda.testing.sandboxes.VirtualNodeLoader
import net.corda.v5.base.util.loggerFor
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

interface VNodeService {
    fun loadVirtualNode(resourceName: String, holdingIdentity: HoldingIdentity): VirtualNodeInfo
    fun getOrCreateSandbox(holdingIdentity: HoldingIdentity): SandboxGroupContext
    fun unloadVirtualNode(virtualNodeInfo: VirtualNodeInfo)
}

@Suppress("unused")
@Component(service = [ VNodeService::class ])
class VNodeServiceImpl @Activate constructor(
    @Reference
    private val flowSandboxService: FlowSandboxService,

    @Reference
    private val cpiLoader: CpiLoader,

    @Reference
    private val virtualNodeLoader: VirtualNodeLoader
) : VNodeService {
    private val logger = loggerFor<VNodeService>()

    override fun loadVirtualNode(resourceName: String, holdingIdentity: HoldingIdentity): VirtualNodeInfo {
        return virtualNodeLoader.loadVirtualNode(resourceName, holdingIdentity)
    }

    override fun unloadVirtualNode(virtualNodeInfo: VirtualNodeInfo) {
        val cpi = cpiLoader.getCpiMetadata(virtualNodeInfo.cpiIdentifier).get()
            ?: throw IllegalStateException("No such CPI ${virtualNodeInfo.cpiIdentifier}")
        virtualNodeLoader.unloadVirtualNode(virtualNodeInfo)
        virtualNodeLoader.forgetCPI(cpi.id)
        cpiLoader.removeCpiMetadata(cpi.id)
    }

    override fun getOrCreateSandbox(holdingIdentity: HoldingIdentity): SandboxGroupContext {
        return flowSandboxService.get(holdingIdentity)
    }
}
