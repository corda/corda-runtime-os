package net.corda.cpiinfo.read.impl

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import java.util.concurrent.ConcurrentHashMap
import net.corda.data.packaging.CpiIdentifier as CpiIdentifierAvro
import net.corda.data.packaging.CpiMetadata as CpiMetadataAvro

/**
 * Map of [CpiIdentifier] to [CpiMetadata] AVRO data objects
 *
 * We use the [toCorda()] methods to convert the Avro objects to Corda ones.
 */
internal class CpiInfoMap {
    private val metadataById = ConcurrentHashMap<CpiIdentifier, CpiMetadata>()

    /** Clear all content */
    fun clear() = metadataById.clear()

    /** Get all CpiMetadata */
    fun getAll(): Map<CpiIdentifier, CpiMetadata> =
        metadataById

    /** Put (store/merge) the incoming map */
    fun putAll(incoming: Map<CpiIdentifierAvro, CpiMetadataAvro>) =
        incoming.forEach { (key, value) -> put(key, value) }

    /** Putting a null value removes the [CpiMetadataAvro] from the maps. */
    fun put(key: CpiIdentifierAvro, value: CpiMetadataAvro?) {
        val convertedKey = CpiIdentifier.fromAvro(key)
        if (value != null) {
            if (key != value.id) {
                throw IllegalArgumentException("Trying to add a Cpi.Metadata for an incorrect Cpi.Identifier")
            }
            metadataById[convertedKey] = CpiMetadata.fromAvro(value)
        } else {
            remove(convertedKey)
        }
    }

    /**
     * Get a [CpiMetadata] by [CpiIdentifier]
     */
    fun get(id: CpiIdentifier): CpiMetadata? = metadataById[id]

    /**
     * Remove the [CpiMetadata] from this collection and return it.
     */
    fun remove(key: CpiIdentifier): CpiMetadata? = metadataById.remove(key)

    /**
     * Remove the [CpiMetadata] from this collection and return it.
     */
    fun remove(key: CpiIdentifierAvro): CpiMetadata? = metadataById.remove(CpiIdentifier.fromAvro(key))
}
