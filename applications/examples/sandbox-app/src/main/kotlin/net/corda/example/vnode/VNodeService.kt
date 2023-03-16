package net.corda.example.vnode

import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.testing.sandboxes.CpiLoader
import net.corda.testing.sandboxes.VirtualNodeLoader
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.CompletableFuture

interface VNodeService {
    fun loadVirtualNode(resourceName: String, holdingIdentity: HoldingIdentity): VirtualNodeInfo
    fun getOrCreateSandbox(holdingIdentity: HoldingIdentity, cpiIdentifier: CpiIdentifier): SandboxGroupContext
    fun unloadVirtualNode(virtualNodeInfo: VirtualNodeInfo)
    fun flushSandboxCache(): CompletableFuture<*>
    @Throws(InterruptedException::class)
    fun waitForSandboxCache(completion: CompletableFuture<*>, duration: Duration): Boolean
}

@Suppress("unused")
@Component(service = [ VNodeService::class ])
class VNodeServiceImpl @Activate constructor(
    @Reference
    private val flowSandboxService: FlowSandboxService,

    @Reference
    private val sandboxGroupContextComponent: SandboxGroupContextComponent,

    @Reference
    private val cpiLoader: CpiLoader,

    @Reference
    private val virtualNodeLoader: VirtualNodeLoader
) : VNodeService {
    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        sandboxGroupContextComponent.initCaches(1)
    }

    override fun loadVirtualNode(resourceName: String, holdingIdentity: HoldingIdentity): VirtualNodeInfo {
        return virtualNodeLoader.loadVirtualNode(resourceName, holdingIdentity)
    }

    override fun unloadVirtualNode(virtualNodeInfo: VirtualNodeInfo) {
        val cpiMetadata = cpiLoader.getCpiMetadata(virtualNodeInfo.cpiIdentifier).get()
            ?: throw IllegalStateException("No such CPI ${virtualNodeInfo.cpiIdentifier}")
        virtualNodeLoader.unloadVirtualNode(virtualNodeInfo)
        virtualNodeLoader.forgetCPI(cpiMetadata.cpiId)
        cpiLoader.removeCpiMetadata(cpiMetadata.cpiId)
    }

    override fun getOrCreateSandbox(holdingIdentity: HoldingIdentity, cpiIdentifier: CpiIdentifier): SandboxGroupContext {
        val cpiMetadata = cpiLoader.getCpiMetadata(cpiIdentifier).get()
            ?: throw IllegalStateException("No such CPI $cpiIdentifier")
        val cpks = cpiMetadata.cpksMetadata.map { it.fileChecksum }.toSet()

        return flowSandboxService.get(holdingIdentity, cpks)
    }

    override fun flushSandboxCache(): CompletableFuture<*> {
        return sandboxGroupContextComponent.flushCache()
    }

    @Throws(InterruptedException::class)
    override fun waitForSandboxCache(completion: CompletableFuture<*>, duration: Duration): Boolean {
        return sandboxGroupContextComponent.waitFor(completion, duration)
    }
}
