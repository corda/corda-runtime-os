package net.corda.flow.sandbox

import net.corda.flow.manager.FlowSandboxService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

class CloseableSandboxGroupContext(private val context: SandboxGroupContext)
    : SandboxGroupContext by context, AutoCloseable {
    override fun close() {
        (context as? AutoCloseable)?.close()
    }
}

@Component(service = [ SandboxLoader::class ])
class SandboxLoader @Activate constructor(
    @Reference
    private val loader: LoaderService,
    @Reference
    private val vnodeInfoService: VirtualNodeInfoService,
    @Reference
    private val flowSandboxService: FlowSandboxService
) {
    fun loadCPI(resourceName: String, holdingIdentity: HoldingIdentity): VirtualNodeInfo {
        val cpi = loader.loadCPI(resourceName)
        return VirtualNodeInfo(holdingIdentity, cpi.metadata.id).also { vnodeInfo ->
            vnodeInfoService.put(vnodeInfo)
        }
    }

    fun unloadCPI(virtualNodeInfo: VirtualNodeInfo) {
        vnodeInfoService.remove(virtualNodeInfo)
        val cpi = loader.get(virtualNodeInfo.cpiIdentifier).get()
            ?: throw IllegalStateException("No such CPI ${virtualNodeInfo.cpiIdentifier}")
        cpi.use(loader::unloadCPI)
    }

    fun getOrCreateSandbox(holdingIdentity: HoldingIdentity): CloseableSandboxGroupContext {
        return CloseableSandboxGroupContext(flowSandboxService.get(holdingIdentity))
    }
}
