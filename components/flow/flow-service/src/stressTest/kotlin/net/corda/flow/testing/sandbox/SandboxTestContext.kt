package net.corda.flow.testing.sandbox

import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [SandboxTestContext::class])
class SandboxTestContext @Activate constructor(
    @Reference(service = SandboxGroupContextComponent::class)
    val sandboxGroupContextComponent: SandboxGroupContextComponent,
    @Reference(service = VirtualNodeInfoReadService::class)
    val virtualNodeInfoReadService: VirtualNodeInfoReadService
) {

}