package net.corda.cpiinfo.read

import net.corda.lifecycle.Lifecycle
import net.corda.packaging.CPI

interface CpiInfoReadService : Lifecycle {
    /**
     * Returns a CPI metadata for a given identifier, or `null` if not found.
     */
    fun get(identifier: CPI.Identifier): CPI.Metadata?

    /**
     * Register the [CpiInfoListener] callback to be notified on all [CPI.Metadata] changes
     */
    fun registerCallback(listener: CpiInfoListener): AutoCloseable
}
