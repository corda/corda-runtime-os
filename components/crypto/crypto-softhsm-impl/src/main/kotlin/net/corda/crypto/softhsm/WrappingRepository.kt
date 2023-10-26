package net.corda.crypto.softhsm

import net.corda.crypto.persistence.WrappingKeyInfo
import java.io.Closeable
import java.util.UUID

/**
 *
 * Database operations for wrapping keys. Implementation of this interface will
 * cover access to specific databases for specific tenants.
 *
 * Follows the JPA repository pattern.
 */
interface WrappingRepository : Closeable {
    /**
     * Save a wrapping key to the database
     *
     * @param alias A name for the wrapping key, must be unique within the tenant.
     * @param key The key material and metadata about version and algorithm.
     * @return The wrapping key that was persisted.
     */
    fun saveKey(alias: String, key: WrappingKeyInfo): WrappingKeyInfo

    /**
     * Update a wrapping key in the database
     *
     * @param alias A name for the wrapping key, must be unique within the tenant.
     * @param key The key material and metadata about version and algorithm.
     * @param id An id of the wrapping key to be updated. If id is provided it updates the row,
     *           otherwise it creates a new entry with random id.
     * @return The wrapping key that was persisted.
     */
    fun saveKeyWithId(alias: String, key: WrappingKeyInfo, id: UUID?): WrappingKeyInfo

    /**
     * Find a wrapping key in the database
     *
     * @param alias The name for the wrapping key, as previously passed into save.
     * @return The wrapping key material and metadata about version and algorithm.
     */
    fun findKey(alias: String): WrappingKeyInfo?

    /**
     * Find a wrapping key in the database
     *
     * @param alias The name for the  wrapping key, as previously passed into save.
     * @return The pair of UUID and the wrapping key material with metadata about version and algorithm.
     */
    fun findKeyAndId(alias: String): Pair<UUID, WrappingKeyInfo>?

    /**
     * Find all wrapping key in the database wrapping. This function will stream its output.
     *
     * @param alias The name of the parent wrapping key.
     * @return A stream of results
     */
    fun findKeysWrappedByAlias(alias: String): Sequence<WrappingKeyInfo>
}
