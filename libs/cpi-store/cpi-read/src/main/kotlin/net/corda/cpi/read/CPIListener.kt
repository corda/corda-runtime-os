package net.corda.cpi.read

import net.corda.packaging.CPI

fun interface CPIListener {
    /**
     * The implementation of this functional class will be used to notify you of the current list of CPI Identifiers
     * and CPI metadata.
     * @param changedKeys the list of changed keys
     * @param currentSnapshot the current snapshot of CPIs
     */
    fun onUpdate(changedKeys: Set<CPI.Identifier>, currentSnapshot: Map<CPI.Identifier, CPI.Metadata>)
}