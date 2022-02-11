package net.corda.testing.sandboxes

import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo

interface SandboxLoader {
    fun loadCPI(resourceName: String, holdingIdentity: HoldingIdentity): VirtualNodeInfo
    fun unloadCPI(virtualNodeInfo: VirtualNodeInfo)
}
