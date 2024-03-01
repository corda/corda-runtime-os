package net.corda.testing.sandboxes.testkit

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.virtualnode.VirtualNodeInfo
import java.util.concurrent.CompletableFuture

interface VirtualNodeService {
    fun loadVirtualNode(resourceName: String): VirtualNodeInfo
    fun releaseVirtualNode(virtualNodeContext: VirtualNodeContext): CompletableFuture<*>?
    fun unloadVirtualNode(completion: CompletableFuture<*>)
    fun getCpiMetadata(id: CpiIdentifier): CompletableFuture<CpiMetadata?>
}
