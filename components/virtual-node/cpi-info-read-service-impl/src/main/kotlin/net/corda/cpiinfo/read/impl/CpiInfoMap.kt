package net.corda.cpiinfo.read.impl

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import java.util.Collections
import net.corda.data.packaging.CpiIdentifier as CpiIdentifierAvro
import net.corda.data.packaging.CpiMetadata as CpiMetadataAvro

/**
 * Map of [CpiIdentifierAvro] to [CpiMetadataAvro] AVRO data objects
 *
 * We use the [toCorda()] methods to convert the Avro objects to Corda ones.
 */
internal class CpiInfoMap {
    private val metadataById: MutableMap<CpiIdentifierAvro, CpiMetadataAvro> =
        Collections.synchronizedMap(mutableMapOf())

    /** Clear all content */
    fun clear() = metadataById.clear()

    /** Get the Avro objects as Corda objects */
    fun getAllAsCordaObjects(): Map<CpiIdentifier, CpiMetadata> =
        metadataById
            .mapKeys { CpiIdentifier.fromAvro(it.key) }
            .mapValues { CpiMetadata.fromAvro(it.value) }

    /** Put (store/merge) the incoming map */
    fun putAll(incoming: Map<CpiIdentifierAvro, CpiMetadataAvro>) =
        incoming.forEach { (key, value) -> put(key, value) }

    /** Put [CpiMetadataAvro] into internal maps. */
    private fun putValue(key: CpiIdentifierAvro, value: CpiMetadataAvro) {
        metadataById[key] = value
    }

    /** Putting a null value removes the [CpiMetadataAvro] from the maps. */
    fun put(key: CpiIdentifierAvro, value: CpiMetadataAvro?) {
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
     * Get all [CpiMetadataAvro]
     */
    fun getAll(): List<CpiMetadataAvro> = metadataById.values.toList()

    /**
     * Get a [CpiMetadataAvro] by [CpiIdentifierAvro]
     */
    fun get(id: CpiIdentifierAvro): CpiMetadataAvro? = metadataById[id]

    /**
     * Remove the [CpiMetadataAvro] from this collection and return it.
     */
    fun remove(key: CpiIdentifierAvro): CpiMetadataAvro? = metadataById.remove(key)
}
