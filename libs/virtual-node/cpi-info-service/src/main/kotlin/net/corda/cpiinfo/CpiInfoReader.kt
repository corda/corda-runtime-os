package net.corda.cpiinfo

import net.corda.packaging.CPI

/**
 * CPI Info service.  Returns CPI metadata for a given identifier.
 */
interface CpiInfoReader {
    /**
     * Returns a CPI metadata for a given identifier, or `null` if not found.
     */
    fun get(identifier: CPI.Identifier): CPI.Metadata?

    fun registerCallback(listener: CpiInfoListener): AutoCloseable
}
