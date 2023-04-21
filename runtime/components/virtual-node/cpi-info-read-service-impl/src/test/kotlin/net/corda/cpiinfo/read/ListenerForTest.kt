package net.corda.cpiinfo.read

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata

class ListenerForTest : CpiInfoListener {
    var update = false
    var lastSnapshot = mapOf<CpiIdentifier, CpiMetadata>()
    var changedKeys = emptySet<CpiIdentifier>()

    override fun onUpdate(
        changedKeys: Set<CpiIdentifier>,
        currentSnapshot: Map<CpiIdentifier, CpiMetadata>
    ) {
        update = true
        lastSnapshot = currentSnapshot
        this.changedKeys = changedKeys

    }
}
