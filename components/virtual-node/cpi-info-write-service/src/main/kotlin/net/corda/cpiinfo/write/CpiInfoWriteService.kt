package net.corda.cpiinfo.write

import net.corda.libs.packaging.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.lifecycle.Lifecycle
import net.corda.reconciliation.ReconcilerWriter

/**
 * Cpi Info writer interface.  The [CpiMetadata] contains its own
 * key, [CpiIdentifier].
 *
 * This interface complements [CpiInfoReadService]
 */
interface CpiInfoWriteService : ReconcilerWriter<CpiMetadata>, Lifecycle {
    /** Put a new [CpiMetadata] into some implementation (e.g. a Kafka component) */
    @Suppress("parameter_name_changed_on_override")
    override fun put(cpiMetadata: CpiMetadata)

    /** Remove [CpiMetadata] some implementation (e.g. a Kafka component) */
    fun remove(cpiMetadata: CpiMetadata)
}