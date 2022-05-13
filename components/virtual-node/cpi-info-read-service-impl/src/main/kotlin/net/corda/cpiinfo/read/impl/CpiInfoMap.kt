package net.corda.cpiinfo.read.impl

import net.corda.data.packaging.CpiIdentifier
import net.corda.data.packaging.CpiMetadata
import net.corda.packaging.Cpi
import net.corda.packaging.converters.toCorda
import java.util.Collections

/**
 * Map of [CpiIdentifier] to [CpiMetadata] AVRO data objects
 *
 * We use the [toCorda()] methods to convert the Avro objects to Corda ones.
 */
internal class CpiInfoMap {
    private val metadataById: MutableMap<CpiIdentifier, CpiMetadata> =
        Collections.synchronizedMap(mutableMapOf())

    /** Clear all content */
    fun clear() = metadataById.clear()

    /** Get the Avro objects as Corda objects */
    fun getAllAsCordaObjects(): Map<Cpi.Identifier, Cpi.Metadata> =
        metadataById
            .mapKeys { it.key.toCorda() }
            .mapValues { it.value.toCorda() }

    /** Put (store/merge) the incoming map */
    fun putAll(incoming: Map<CpiIdentifier, CpiMetadata>) =
        incoming.forEach { (key, value) -> put(key, value) }

    /** Put [CpiMetadata] into internal maps. */
    private fun putValue(key: CpiIdentifier, value: CpiMetadata) {
        metadataById[key] = value
    }

    /** Putting a null value removes the [CpiMetadata] from the maps. */
    fun put(key: CpiIdentifier, value: CpiMetadata?) {
        if (value != null) {
            if (key != value.id) {
                throw IllegalArgumentException("Trying to add a Cpi.Metadata for an incorrect Cpi.Identifier")
            }
            putValue(key, value)
        } else {
            remove(key)
        }
    }

    /**
     * Get all [CpiMetadata]
     */
    fun getAll(): List<CpiMetadata> = metadataById.values.toList()

    /**
     * Get a [CpiMetadata] by [CpiIdentifier]
     */
    fun get(id: CpiIdentifier): CpiMetadata? = metadataById[id]

    /**
     * Remove the [CpiMetadata] from this collection and return it.
     */
    fun remove(key: CpiIdentifier): CpiMetadata? = metadataById.remove(key)
}
