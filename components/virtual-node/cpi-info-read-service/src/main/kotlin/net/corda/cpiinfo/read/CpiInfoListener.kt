package net.corda.cpiinfo.read

import net.corda.libs.packaging.Cpi

/**
 * Functional interface that notifies us of any changes to a [Cpi.Identifier] and its [Cpi.Metadata]
 */
fun interface CpiInfoListener {
    fun onUpdate(changedKeys: Set<Cpi.Identifier>, currentSnapshot: Map<Cpi.Identifier, Cpi.Metadata>)
}
