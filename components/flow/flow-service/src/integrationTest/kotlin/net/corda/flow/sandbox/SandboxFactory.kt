package net.corda.flow.sandbox

import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.testing.sandboxes.VirtualNodeLoader
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
    private val virtualNodeLoader: VirtualNodeLoader,
    @Reference
    private val flowSandboxService: FlowSandboxService,
    @Reference(service = SandboxGroupContextComponent::class)
    private val sandboxGroupContextComponent: SandboxGroupContextComponent,
) {
    init {
        sandboxGroupContextComponent.initCache(1)
    }
    fun loadVirtualNode(resourceName: String, holdingIdentity: HoldingIdentity): VirtualNodeInfo {
        return virtualNodeLoader.loadVirtualNode(resourceName, holdingIdentity)
    }

    fun unloadVirtualNode(virtualNodeInfo: VirtualNodeInfo) {
        virtualNodeLoader.unloadVirtualNode(virtualNodeInfo)
    }

    fun getOrCreateSandbox(holdingIdentity: HoldingIdentity): CloseableSandboxGroupContext {
        return CloseableSandboxGroupContext(flowSandboxService.get(holdingIdentity))
    }
}
