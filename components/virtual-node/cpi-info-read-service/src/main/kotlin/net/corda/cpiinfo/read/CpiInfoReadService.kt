package net.corda.cpiinfo.read

import net.corda.libs.packaging.CpiIdentifier
import net.corda.libs.packaging.CpiMetadata
import net.corda.lifecycle.Lifecycle

interface CpiInfoReadService : Lifecycle {
    /**
     * Returns a list of all CPI metadata, or empty list if no CPI metadata found.
     */
    fun getAll(): List<CpiMetadata>

    /**
     * Returns a CPI metadata for a given identifier, or `null` if not found.
     */
    fun get(identifier: CpiIdentifier): CpiMetadata?

    /**
     * Register the [CpiInfoListener] callback to be notified on all [CpiMetadata] changes
     */
    fun registerCallback(listener: CpiInfoListener): AutoCloseable
}
