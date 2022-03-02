package net.corda.cpiinfo.write

import net.corda.libs.packaging.CpiMetadata
import net.corda.lifecycle.Lifecycle

/**
 * Cpi Info writer interface.  The [CpiMetadata] contains its own
 * key, [CpiIdentifier].
 *
 * This interface complements [CpiInfoReadService]
 */
interface CpiInfoWriteService : Lifecycle  {
    /** Put a new [CpiMetadata] into some implementation (e.g. a Kafka component) */
    fun put(cpiMetadata: CpiMetadata)

    /** Remove [CpiMetadata] some implementation (e.g. a Kafka component) */
    fun remove(cpiMetadata: CpiMetadata)
}
