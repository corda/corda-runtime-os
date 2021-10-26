package net.corda.cpi.read

import net.corda.packaging.CPI

fun interface CPIListener {
    /**
     * The implementation of this functional class will be used to notify you of the current list of CPI Identifiers
     * @param cpiIdentifiers the complete known set of cpi Identifiers
     */
    fun onUpdate(changedKeys: Set<CPI.Identifier>, currentSnapshot: Map<CPI.Identifier, CPI.Metadata>)
}