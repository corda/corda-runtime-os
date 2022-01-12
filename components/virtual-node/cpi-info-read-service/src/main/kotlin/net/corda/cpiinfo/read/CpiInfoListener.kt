package net.corda.cpiinfo.read

import net.corda.packaging.CPI

/**
 * Functional interface that notifies us of any changes to a [CPI.Identifier] and its [CPI.Metadata]
 */
fun interface CpiInfoListener {
    fun onUpdate(changedKeys: Set<CPI.Identifier>, currentSnapshot: Map<CPI.Identifier, CPI.Metadata>)
}
