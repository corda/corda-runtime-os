package net.corda.virtualnode

import net.corda.data.identity.HoldingIdentity
import net.corda.data.virtualnode.VirtualNodeInfo

interface VirtualNodeInfoWriterService {
    fun put(holdingIdentity: HoldingIdentity, virtualNodeInfo: VirtualNodeInfo)
    fun remove(holdingIdentity: HoldingIdentity)
}
