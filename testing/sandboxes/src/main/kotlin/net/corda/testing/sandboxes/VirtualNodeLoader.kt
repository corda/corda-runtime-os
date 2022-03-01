package net.corda.testing.sandboxes

import net.corda.libs.packaging.CpiIdentifier
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo

interface VirtualNodeLoader {
    fun loadVirtualNode(resourceName: String, holdingIdentity: HoldingIdentity): VirtualNodeInfo
    fun unloadVirtualNode(virtualNodeInfo: VirtualNodeInfo)
    fun forgetCPI(id: CpiIdentifier)
}
