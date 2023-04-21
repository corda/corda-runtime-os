package net.corda.cpiinfo.read

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.lifecycle.Lifecycle
import net.corda.reconciliation.ReconcilerReader

interface CpiInfoReadService : ReconcilerReader<CpiIdentifier, CpiMetadata>, Lifecycle {
    /**
     * Returns a list of all CPI metadata, or empty list if no CPI metadata found.
     */
    fun getAll(): Collection<CpiMetadata>

    /**
     * Returns a CPI metadata for a given identifier, or `null` if not found.
     */
    fun get(identifier: CpiIdentifier): CpiMetadata?
}