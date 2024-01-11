package net.corda.test.flow.util

import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.testing.sandboxes.VirtualNodeLoader
import net.corda.testing.sandboxes.testkit.RequireSandboxTestkit
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@RequireSandboxTestkit
@Component(service = [VirtualNodeCreationService::class])
class VirtualNodeCreationService @Activate constructor(
    @Reference
    private val virtualNodeLoader: VirtualNodeLoader,
    @Reference
    val sandboxGroupContextComponent: SandboxGroupContextComponent
) {

    init {
        sandboxGroupContextComponent.resizeCaches(0)
    }

    fun load(resourceName: String, holdingIdentity: HoldingIdentity): VirtualNodeInfo {
        return virtualNodeLoader.loadVirtualNode(resourceName, holdingIdentity)
    }
}