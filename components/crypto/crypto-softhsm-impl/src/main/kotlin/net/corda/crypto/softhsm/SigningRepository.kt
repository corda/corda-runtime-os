package net.corda.crypto.softhsm

import java.security.PublicKey
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.SigningKeyInfo
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.v5.crypto.SecureHash
import java.io.Closeable

/**
 * Crypto JPA repository
 *
 * See https://thorben-janssen.com/implementing-the-repository-pattern-with-jpa-and-hibernate/
 */
interface SigningRepository : Closeable {
    /**
     * Saving a new key information.
     *
     * @param context data about the key store (the DTO)
     * @param replace allow keys to be replaced in the database normally this is used
     *                to add keys. If this is set it is permitted to replace the
     *                records for a key pair. There's no problem setting replace to true
     *                when adding a fresh key.
     *
     * @throws [IllegalStateException] if the key already exists and replace is false.
     */
    fun savePrivateKey(context: SigningWrappedKeySaveContext, replace: Boolean = false): SigningKeyInfo

    /**
     * Find a key record by its alias.
     */
    fun findKey(alias: String): SigningKeyInfo?

    /**
     * Find a key record by the public key.
     */
    fun findKey(publicKey: PublicKey): SigningKeyInfo?

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
    fun query(
        skip: Int,
        take: Int,
        orderBy: SigningKeyOrderBy,
        filter: Map<String, String>,
    ): Collection<SigningKeyInfo>

    /**
     * Looks for keys by key ids.
     *
     * @param keyIds Key ids to look keys for.
     */
    fun lookupByPublicKeyShortHashes(keyIds: Set<ShortHash>): Collection<SigningKeyInfo>

    /**
     * Looks for keys by full key ids.
     *
     * @param fullKeyIds Key ids to look keys for.
     */
    fun lookupByPublicKeyHashes(fullKeyIds: Set<SecureHash>): Collection<SigningKeyInfo>

}