package net.corda.cpi.info.service

import net.corda.packaging.CPI

/**
 * CPI Info service.  Returns CPI metadata for a given identifier.
 */
interface CpiInfoService {
    /**
     * Returns a CPI metadata for a given identifier, or `null` if not found.
     */
    fun get(identifier: CPI.Identifier): CPI.Metadata?
}
