package net.corda.cpiinfo

import net.corda.packaging.CPI

/**
 * Cpi Info writer interface.  The [CPI.Metadata] contains its own
 * key, [CPI.Identifier].
 *
 * This interface complements [CpiInfoReader]
 */
interface CpiInfoWriter {
    /** Put a new [CPI.Metadata] into some implementation (e.g. a Kafka component) */
    fun put(cpiMetadata: CPI.Metadata)
    
    /** Remove [CPI.Metadata] some implementation (e.g. a Kafka component) */
    fun remove(cpiMetadata: CPI.Metadata)
}
