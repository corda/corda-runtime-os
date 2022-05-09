package net.corda.virtualnode.read.fake

import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoListener

internal class VirtualNodeInfoListenerSpy : VirtualNodeInfoListener {

    private val _keys = mutableListOf<Set<HoldingIdentity>>()
    private val _snapshots = mutableListOf<Map<HoldingIdentity, VirtualNodeInfo>>()

    val keys: List<Set<HoldingIdentity>>
        get() = _keys

    val snapshots: List<Map<HoldingIdentity, VirtualNodeInfo>>
        get() = _snapshots

    val timesCalled: Int
        get() = _keys.size

    override fun onUpdate(
        changedKeys: Set<HoldingIdentity>,
        currentSnapshot: Map<HoldingIdentity, VirtualNodeInfo>,
    ) {
        _keys += changedKeys
        _snapshots += currentSnapshot
    }
}