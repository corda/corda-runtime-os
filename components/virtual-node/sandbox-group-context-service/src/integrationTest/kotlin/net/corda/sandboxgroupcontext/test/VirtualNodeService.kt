package net.corda.sandboxgroupcontext.test

import net.corda.packaging.CPK
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.testing.sandboxes.CpiLoaderService
import net.corda.v5.application.flows.Flow
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.write.VirtualNodeInfoWriteService
import org.junit.jupiter.api.fail
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference

@Component(service = [ VirtualNodeService::class ])
class VirtualNodeService @Activate constructor(
    @Reference
    private val cpiLoader: CpiLoaderService,

    @Reference
    private val vnodeInfoWriter: VirtualNodeInfoWriteService,

    @Reference
    private val sandboxGroupContextComponent: SandboxGroupContextComponent
) {
    @Suppress("unused")
    @Deactivate
    fun done() {
        sandboxGroupContextComponent.close()
    }

    fun loadCPI(resourceName: String, holdingIdentity: HoldingIdentity): VirtualNodeInfo {
        val cpi = cpiLoader.loadCPI(resourceName)
        return VirtualNodeInfo(holdingIdentity, cpi.metadata.id)
            .also(vnodeInfoWriter::put)
    }

    fun unloadCPI(virtualNodeInfo: VirtualNodeInfo) {
        vnodeInfoWriter.remove(virtualNodeInfo)
    }

    fun getOrCreateSandbox(virtualNodeInfo: VirtualNodeInfo): SandboxGroupContext {
        val cpi = cpiLoader.get(virtualNodeInfo.cpiIdentifier).get()
            ?: fail("CPI ${virtualNodeInfo.cpiIdentifier} not found")
        val vNodeContext = VirtualNodeContext(
            virtualNodeInfo.holdingIdentity,
            cpi.metadata.cpks.mapTo(LinkedHashSet(), CPK.Metadata::id),
            SandboxGroupType.FLOW,
            SingletonSerializeAsToken::class.java,
            null
        )
        return sandboxGroupContextComponent.getOrCreate(vNodeContext) { _, sandboxGroupContext ->
            sandboxGroupContextComponent.registerCustomCryptography(sandboxGroupContext)
        }
    }

    fun <T : Any> runFlow(className: String, groupContext: SandboxGroupContext): T {
        val workflowClass = groupContext.sandboxGroup.loadClassFromMainBundles(className, Flow::class.java)
        val context = FrameworkUtil.getBundle(workflowClass).bundleContext
        val reference = context.getServiceReferences(Flow::class.java, "(component.name=$className)")
            .firstOrNull() ?: fail("No service found for $className.")
        return context.getService(reference)?.let { service ->
            try {
                @Suppress("unchecked_cast")
                service.call() as? T ?: fail("Workflow did not return the correct type.")
            } finally {
                context.ungetService(reference)
            }
        } ?: fail("$className service not available - OSGi error?")
    }
}
