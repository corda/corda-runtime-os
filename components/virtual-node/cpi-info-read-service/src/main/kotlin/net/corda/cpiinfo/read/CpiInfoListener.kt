package net.corda.cpiinfo.read

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata

/**
 * Functional interface that notifies us of any changes to a [CpiIdentifier] and its [CpiMetadata]
 */
fun interface CpiInfoListener {
    fun onUpdate(changedKeys: Set<CpiIdentifier>, currentSnapshot: Map<CpiIdentifier, CpiMetadata>)
}
