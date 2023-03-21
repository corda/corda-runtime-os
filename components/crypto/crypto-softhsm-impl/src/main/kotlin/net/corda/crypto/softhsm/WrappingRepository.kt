package net.corda.crypto.softhsm

import net.corda.crypto.persistence.WrappingKeyInfo
import java.io.Closeable

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
     */
    fun saveKey(alias: String, key: WrappingKeyInfo)

    /**
     * Find a wrapping key in the database
     *
     * @param alias The name for the  wrapping key, as previously passed into save.
     * @return The wrapping key material and metadatabout about version and algoirthm.
     */
    fun findKey(alias: String): WrappingKeyInfo?
}