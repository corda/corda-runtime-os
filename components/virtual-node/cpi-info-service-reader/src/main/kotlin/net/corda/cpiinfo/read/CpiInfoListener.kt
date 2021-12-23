package net.corda.cpiinfo.read

import net.corda.packaging.CPI

fun interface CpiInfoListener {
    fun onUpdate(changedKeys: Set<CPI.Identifier>, currentSnapshot: Map<CPI.Identifier, CPI.Metadata>)
}
