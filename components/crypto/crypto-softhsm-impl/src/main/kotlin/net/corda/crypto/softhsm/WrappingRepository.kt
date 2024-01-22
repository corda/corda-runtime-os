package net.corda.crypto.softhsm

import net.corda.crypto.persistence.WrappingKeyInfo
import java.io.Closeable
import java.util.UUID

/**
 *
 * Database operations for wrapping keys. Implementation of this interface will cover access to specific databases for
 * specific tenants and in all but one case that will mean the results will be limited only to that tenant.
 *
 * The exception to this is when [WrappingRepository] is bound to the cluster crypto database. In this case methods will
 * return wrapping keys for any cluster tenant type. Where a single wrapping key is returned, you will always get back
 * a wrapping key for a specific tenant anyway, because both the id and alias of the wrapping keys are unique to one
 * tenant. In the case of [findKeysWrappedByParentKey] you will get all wrapping keys for all cluster tenants.
 *
 * In all cases, where there are more than one wrapping key per alias with different generation numbers, you will only
 * receive the wrapping key with the highest generation number.
 *
 * Follows the JPA repository pattern.
 */
interface WrappingRepository : Closeable {
    /**
     * Save a wrapping key to the database
     *
     * @param key The key material and metadata about version and algorithm.
     * @return The wrapping key that was persisted.
     */
    fun saveKey(key: WrappingKeyInfo): WrappingKeyInfo

    /**
     * Update a wrapping key in the database
     *
     * @param key The key material and metadata about version and algorithm.
     * @param id An id of the wrapping key to be updated. If id is provided it updates the row,
     *           otherwise it creates a new entry with random id.
     * @return The wrapping key that was persisted.
     */
    fun saveKeyWithId(key: WrappingKeyInfo, id: UUID?): WrappingKeyInfo

    /**
     * Find a wrapping key in the database. The wrapping key with the highest generation is always returned.
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
     * Find all wrapping keys in the database which were wrapped by a specific parent key. As noted above, if you run
     * this method on the cluster crypto db (as opposed to a vnode cypto db) you will get results that span multiple
     * tenants. As each alias is unique across the entire cluster crypto db (i.e. cluster crypto tenants will not share
     * an alias) you will never get back a list where aliases are used in more than one tenant even when called on a
     * crypto cluster db.
     *
     * @param parentKeyAlias The name of the parent wrapping key.
     * @return Information about each wrapping key, in a list
     */
    fun findKeysWrappedByParentKey(parentKeyAlias: String): List<WrappingKeyInfo>

    /**
     * Find the wrapping key associated with the provided UUID
     *
     * @param id The UUID of the wrapping key
     * @return Information about the requested wrapping key if it exists
     */
    fun getKeyById(id: UUID): WrappingKeyInfo?
}
