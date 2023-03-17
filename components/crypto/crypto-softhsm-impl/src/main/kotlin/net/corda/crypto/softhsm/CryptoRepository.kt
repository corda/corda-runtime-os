package net.corda.crypto.softhsm

import java.io.Closeable
import java.security.PublicKey
import net.corda.crypto.core.ShortHash
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningKeySaveContext
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.v5.crypto.SecureHash

/**
 * Crypto JPA repository
 *
 * See https://thorben-janssen.com/implementing-the-repository-pattern-with-jpa-and-hibernate/
 */
interface CryptoRepository : Closeable {
    fun saveWrappingKey(alias: String, key: WrappingKeyInfo)
    fun findWrappingKey(alias: String): WrappingKeyInfo?

    /**
     * Saving a new key information.
     *
     * @throws [IllegalStateException] if the key already exists.
     */
    fun saveSigningKey(tenantId: String, context: SigningKeySaveContext)

    /**
     * Find a key record by its alias.
     */
    fun findSigningKey(tenantId: String, alias: String): SigningCachedKey?

    /**
     * Find a key record by the public key.
     */
    fun findSigningKey(tenantId: String, publicKey: PublicKey): SigningCachedKey?

    /**
     * Returns list of keys satisfying the filter condition. All filter values are combined as AND.
     *
     * @param skip the response paging information, number of records to skip.
     * @param take the response paging information, number of records to return, the actual number may be less than
     * requested.
     * @param orderBy the order by.
     * @param filter the layered property map of the filter parameters such as
     * category (the HSM's category which handles the keys),
     * schemeCodeName (the key's signature scheme name),
     * alias (the alias which is assigned by the tenant),
     * masterKeyAlias (the wrapping key alias),
     * externalId (an id associated with the key),
     * createdAfter (specifies inclusive time after which a key was created),
     * createdBefore (specifies inclusive time before which a key was created).
     */
    fun lookupSigningKey(
        tenantId: String,
        skip: Int,
        take: Int,
        orderBy: SigningKeyOrderBy,
        filter: Map<String, String>,
    ): Collection<SigningCachedKey>

    /**
     * Looks for keys by key ids.
     *
     * @param keyIds Key ids to look keys for.
     */
    fun lookupSigningKeysByIds(
        tenantId: String,
        keyIds: Set<ShortHash>,
    ): Collection<SigningCachedKey>

    /**
     * Looks for keys by full key ids.
     *
     * @param fullKeyIds Key ids to look keys for.
     */
    fun lookupSigningKeysByFullIds(
        tenantId: String,
        fullKeyIds: Set<SecureHash>,
    ): Collection<SigningCachedKey>
}