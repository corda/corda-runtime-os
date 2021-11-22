package net.corda.virtualnode

class ListenerForTest : VirtualNodeInfoListener {
    var update = false
    var lastSnapshot = mapOf<HoldingIdentity, VirtualNodeInfo>()

    override fun onUpdate(
        changedKeys: Set<HoldingIdentity>,
        currentSnapshot: Map<HoldingIdentity, VirtualNodeInfo>
    ) {
        update = true
        lastSnapshot = currentSnapshot
    }
}
