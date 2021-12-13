package net.corda.cpiinfo

import net.corda.packaging.CPI

fun interface CpiInfoListener {
    fun onUpdate(changedKeys: Set<CPI.Identifier>, currentSnapshot: Map<CPI.Identifier, CPI.Metadata>)
}
