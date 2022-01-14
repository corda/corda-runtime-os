package net.corda.cpiinfo.read.impl

import net.corda.data.packaging.CPIIdentifier
import net.corda.data.packaging.CPIMetadata
import net.corda.packaging.CPI
import net.corda.packaging.converters.toCorda
import java.util.Collections

/**
 * Map of [CPI.Identifier] to [CPI.Metadata] AVRO data objects
 *
 * We use the [toCorda()] methods to convert the Avro objects to Corda ones.
 */
internal class CpiInfoMap {
    private val metadataById: MutableMap<CPIIdentifier, CPIMetadata> =
        Collections.synchronizedMap(mutableMapOf())

    /** Clear all content */
    fun clear() = metadataById.clear()

    /** Get the Avro objects as Corda objects */
    fun getAllAsCordaObjects(): Map<CPI.Identifier, CPI.Metadata> =
        metadataById
            .mapKeys { it.key.toCorda() }
            .mapValues { it.value.toCorda() }

    /** Put (store/merge) the incoming map */
    fun putAll(incoming: Map<CPIIdentifier, CPIMetadata>) =
        incoming.forEach { (key, value) -> put(key, value) }

    /** Put [CPI.Metadata] into internal maps. */
    private fun putValue(key: CPIIdentifier, value: CPIMetadata) {
        metadataById[key] = value
    }

    /** Putting a null value removes the [CPI.Metadata] from the maps. */
    fun put(key: CPIIdentifier, value: CPIMetadata?) {
        if (value != null) {
            if (key != value.id) {
                throw IllegalArgumentException("Trying to add a CPI.Metadata for an incorrect CPI.Identifier")
            }
            putValue(key, value)
        } else {
            remove(key)
        }
    }

    /**
     * Get a [CPI.Metadata] by [CPI.Identifier]
     */
    fun get(id: CPIIdentifier): CPIMetadata? = metadataById[id]

    /**
     * Remove the [CPI.Metadata] from this collection and return it.
     */
    fun remove(key: CPIIdentifier): CPIMetadata? = metadataById.remove(key)
}
