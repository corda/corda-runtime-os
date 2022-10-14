package net.corda.db.persistence.testkit.components

import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.SandboxGroupComponent
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.testing.sandboxes.CpiLoader
import net.corda.testing.sandboxes.VirtualNodeLoader
import net.corda.v5.serialization.SingletonSerializeAsToken
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
    val sandboxGroupComponent: SandboxGroupComponent,
) {
    init {
        // Needs this since we do not have a config service running:  it will fail to start otherwise.
        sandboxGroupComponent.initCache(2)
    }
    private companion object {
        private const val X500_NAME = "CN=Testing, OU=Application, O=R3, L=London, C=GB"

        fun generateHoldingIdentity() = createTestHoldingIdentity(X500_NAME, UUID.randomUUID().toString())
    }

    private val vnodes = mutableMapOf<SandboxGroupContext, VirtualNodeInfo>()

    @Suppress("unused")
    @Deactivate
    fun done() {
        sandboxGroupComponent.close()
    }

    private fun getOrCreateSandbox(virtualNodeInfo: VirtualNodeInfo): SandboxGroupContext {
        val cpi = cpiLoader.getCpiMetadata(virtualNodeInfo.cpiIdentifier).get()
            ?: fail("CPI ${virtualNodeInfo.cpiIdentifier} not found")
        val vNodeContext = VirtualNodeContext(
            virtualNodeInfo.holdingIdentity,
            cpi.cpksMetadata.mapTo(LinkedHashSet()) { it.fileChecksum },
            SandboxGroupType.FLOW,
            SingletonSerializeAsToken::class.java,
            null
        )
        return sandboxGroupComponent.getOrCreate(vNodeContext) { _, sandboxGroupContext ->
            sandboxGroupComponent.registerCustomCryptography(sandboxGroupContext)
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
