package net.corda.testing.sandboxes.testkit.impl

import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.testing.sandboxes.CpiLoader
import net.corda.testing.sandboxes.VirtualNodeLoader
import net.corda.testing.sandboxes.testkit.VirtualNodeService
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration.ofSeconds
import java.util.concurrent.CompletableFuture
import java.util.UUID

@Suppress("unused")
@Component(service = [ VirtualNodeService::class ])
class VirtualNodeServiceImpl @Activate constructor(
    @Reference
    private val cpiLoader: CpiLoader,

    @Reference
    private val virtualNodeLoader: VirtualNodeLoader,

    @Reference
    private val sandboxGroupContextComponent: SandboxGroupContextComponent
) : VirtualNodeService {
    private companion object {
        private const val X500_NAME = "CN=Testing, OU=Application, O=R3, L=London, C=GB"
        private val ONE_SECOND = ofSeconds(1)

        private fun generateHoldingIdentity()
            = HoldingIdentity(MemberX500Name.parse(X500_NAME), UUID.randomUUID().toString())
    }

    init {
        sandboxGroupContextComponent.initCache(1)
    }

    override fun loadVirtualNode(resourceName: String): VirtualNodeInfo {
        return virtualNodeLoader.loadVirtualNode(resourceName, generateHoldingIdentity())
    }

    override fun releaseVirtualNode(virtualNodeContext: VirtualNodeContext): CompletableFuture<*>? {
        return sandboxGroupContextComponent.remove(virtualNodeContext)
    }

    /**
     * Wait for a sandbox to be garbage collected. Ensure that invoking test can time out,
     * because the sandbox cannot be collected if it is still referenced somewhere.
     */
    override fun unloadVirtualNode(completion: CompletableFuture<*>) {
        do {
            @Suppress("ExplicitGarbageCollectionCall")
            System.gc()
        } while (!sandboxGroupContextComponent.waitFor(completion, ONE_SECOND))
    }
}
