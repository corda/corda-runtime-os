package net.corda.entityprocessor.impl.tests.components

import net.corda.libs.packaging.CpkMetadata
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.testing.sandboxes.CpiLoader
import net.corda.testing.sandboxes.VirtualNodeLoader
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.junit.jupiter.api.fail
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import java.util.UUID


@Component(service = [VirtualNodeService::class])
class VirtualNodeService @Activate constructor(
    @Reference
    private val cpiLoader: CpiLoader,

    @Reference
    private val virtualNodeLoader: VirtualNodeLoader,

    @Reference
    val sandboxGroupContextComponent: SandboxGroupContextComponent,
) {
    init {
        // Needs this since we do not have a config service running:  it will fail to start otherwise.
        sandboxGroupContextComponent.initCache(2)
    }
    private companion object {
        private const val X500_NAME = "CN=Testing, OU=Application, O=R3, L=London, C=GB"

        fun generateHoldingIdentity() = HoldingIdentity(X500_NAME, UUID.randomUUID().toString())
    }

    private val vnodes = mutableMapOf<SandboxGroupContext, VirtualNodeInfo>()

    @Suppress("unused")
    @Deactivate
    fun done() {
        sandboxGroupContextComponent.close()
    }

    private fun getOrCreateSandbox(virtualNodeInfo: VirtualNodeInfo): SandboxGroupContext {
        val cpi = cpiLoader.getCpiMetadata(virtualNodeInfo.cpiIdentifier).get()
            ?: fail("CPI ${virtualNodeInfo.cpiIdentifier} not found")
        val vNodeContext = VirtualNodeContext(
            virtualNodeInfo.holdingIdentity,
            cpi.cpksMetadata.mapTo(LinkedHashSet(), CpkMetadata::cpkId),
            SandboxGroupType.FLOW,
            SingletonSerializeAsToken::class.java,
            null
        )
        return sandboxGroupContextComponent.getOrCreate(vNodeContext) { _, sandboxGroupContext ->
            sandboxGroupContextComponent.registerCustomCryptography(sandboxGroupContext)
        }
    }

    fun load(resourceName: String) =
        virtualNodeLoader.loadVirtualNode(resourceName, generateHoldingIdentity())

    fun loadSandbox(resourceName: String): SandboxGroupContext {
        val vnodeInfo = virtualNodeLoader.loadVirtualNode(resourceName, generateHoldingIdentity())
        return getOrCreateSandbox(vnodeInfo).also { ctx ->
            vnodes[ctx] = vnodeInfo
        }
    }

    fun unloadSandbox(sandboxGroupContext: SandboxGroupContext) {
        (sandboxGroupContext as? AutoCloseable)?.close()
        vnodes.remove(sandboxGroupContext)?.let(virtualNodeLoader::unloadVirtualNode)
    }
}
