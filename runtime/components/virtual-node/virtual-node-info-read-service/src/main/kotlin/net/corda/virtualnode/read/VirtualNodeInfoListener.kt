package net.corda.virtualnode.read

import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo

fun interface VirtualNodeInfoListener {
    fun onUpdate(changedKeys: Set<HoldingIdentity>, currentSnapshot: Map<HoldingIdentity, VirtualNodeInfo>)
}
