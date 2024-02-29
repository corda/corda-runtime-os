package net.corda.testing.sandboxes

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import java.util.concurrent.CompletableFuture

interface VirtualNodeLoader {
    fun loadVirtualNode(resourceName: String, holdingIdentity: HoldingIdentity): VirtualNodeInfo
    fun unloadVirtualNode(virtualNodeInfo: VirtualNodeInfo)
    fun forgetCPI(id: CpiIdentifier)
    fun getCpiMetadata(id: CpiIdentifier): CompletableFuture<CpiMetadata?>
}
