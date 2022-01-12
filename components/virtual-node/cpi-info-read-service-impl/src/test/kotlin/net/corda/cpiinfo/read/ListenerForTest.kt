package net.corda.cpiinfo.read

import net.corda.packaging.CPI

class ListenerForTest : CpiInfoListener {
    var update = false
    var lastSnapshot = mapOf<CPI.Identifier, CPI.Metadata>()
    var changedKeys = emptySet<CPI.Identifier>()

    override fun onUpdate(
        changedKeys: Set<CPI.Identifier>,
        currentSnapshot: Map<CPI.Identifier, CPI.Metadata>
    ) {
        update = true
        lastSnapshot = currentSnapshot
        this.changedKeys = changedKeys

    }
}
