package net.corda.crypto.softhsm

import java.security.PublicKey
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.SigningKeyInfo
import net.corda.crypto.persistence.SigningKeyMaterialInfo
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.v5.crypto.SecureHash
import java.io.Closeable
import java.util.UUID

/**
 * Crypto JPA repository
 *
 * See https://thorben-janssen.com/implementing-the-repository-pattern-with-jpa-and-hibernate/
 */
interface SigningRepository : Closeable {
    /**
     * Saving a new key information.
     *
     * @throws [IllegalStateException] if the key already exists.
     */
    fun savePrivateKey(context: SigningWrappedKeySaveContext): SigningKeyInfo

    /**
     * Find a key record by its alias.
     */
    fun findKey(alias: String): SigningKeyInfo?

    /**
     * Find a key record by the public key.
     */
    fun findKey(publicKey: PublicKey): SigningKeyInfo?

    /**
     * Returns collection of keys satisfying the filter condition. All filter values are combined as AND.
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

    /**
     * Returns collection of key materials which are wrapped with a specific wrapping key.
     *
     * @param wrappingKeyId the id of the wrapping key of the key materials
     *
     * @return a collection of all key materials all wrapped with the specified wrapping key
     */
    fun getKeyMaterials(wrappingKeyId: UUID): Collection<SigningKeyMaterialInfo>

    /**
     * Saves a signing key material entity to the database after being rewrapped.
     * Note: This is not for use when creating new keys, [savePrivateKey] includes that functionality.
     *
     * @param signingKeyMaterialInfo The signing key material to be saved to the database
     * @param wrappingKeyId The UUID of the wrapping key
     */
    fun saveSigningKeyMaterial(signingKeyMaterialInfo: SigningKeyMaterialInfo, wrappingKeyId: UUID)
}
