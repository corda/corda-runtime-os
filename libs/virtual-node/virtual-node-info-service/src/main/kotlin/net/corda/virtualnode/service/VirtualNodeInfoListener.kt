package net.corda.virtualnode.service

import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo

fun interface VirtualNodeInfoListener {
    fun onUpdate(changedKeys: Set<HoldingIdentity>, currentSnapshot: Map<HoldingIdentity, VirtualNodeInfo>)
}
