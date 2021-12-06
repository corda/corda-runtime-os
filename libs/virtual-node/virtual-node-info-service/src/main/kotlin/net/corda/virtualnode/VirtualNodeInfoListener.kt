package net.corda.virtualnode

fun interface VirtualNodeInfoListener {
    fun onUpdate(changedKeys: Set<HoldingIdentity>, currentSnapshot: Map<HoldingIdentity, VirtualNodeInfo>)
}
