package net.corda.flow.sandbox

import net.corda.flow.manager.FlowSandboxService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.testing.sandboxes.SandboxLoader
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

@Component(service = [ SandboxFactory::class ])
class SandboxFactory @Activate constructor(
    @Reference
    private val sandboxLoader: SandboxLoader,
    @Reference
    private val flowSandboxService: FlowSandboxService
) {
    fun loadCPI(resourceName: String, holdingIdentity: HoldingIdentity): VirtualNodeInfo {
        return sandboxLoader.loadCPI(resourceName, holdingIdentity)
    }

    fun unloadCPI(virtualNodeInfo: VirtualNodeInfo) {
        sandboxLoader.unloadCPI(virtualNodeInfo)
    }

    fun getOrCreateSandbox(holdingIdentity: HoldingIdentity): CloseableSandboxGroupContext {
        return CloseableSandboxGroupContext(flowSandboxService.get(holdingIdentity))
    }
}
