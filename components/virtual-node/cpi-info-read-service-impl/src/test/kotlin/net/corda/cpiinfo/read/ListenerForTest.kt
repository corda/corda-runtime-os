package net.corda.cpiinfo.read

import net.corda.packaging.Cpi

class ListenerForTest : CpiInfoListener {
    var update = false
    var lastSnapshot = mapOf<Cpi.Identifier, Cpi.Metadata>()
    var changedKeys = emptySet<Cpi.Identifier>()

    override fun onUpdate(
        changedKeys: Set<Cpi.Identifier>,
        currentSnapshot: Map<Cpi.Identifier, Cpi.Metadata>
    ) {
        update = true
        lastSnapshot = currentSnapshot
        this.changedKeys = changedKeys

    }
}
