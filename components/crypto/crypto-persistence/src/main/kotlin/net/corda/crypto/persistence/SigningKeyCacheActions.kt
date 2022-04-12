package net.corda.crypto.persistence

import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import java.security.PublicKey
import java.time.Instant

interface SigningKeyCacheActions : AutoCloseable {
    /**
     * Saving a new key information.
     *
     * @throws [CryptoServiceBadRequestException] if the key already exists.
     */
    fun save(context: SigningKeySaveContext)

    /**
     * Find a key record by its alias.
     */
    fun find(alias: String): SigningCachedKey?

    /**
     * Find a key record by the public key.
     */
    fun find(publicKey: PublicKey): SigningCachedKey?

    /**
     * Filters the input [PublicKey]s down to a collection of keys that this tenant owns (has private keys for).
     *
     * @param candidateKeys The list of the keys look up for, the maximum number of items is 20.
     */
    fun filterMyKeys(candidateKeys: Collection<PublicKey>): Collection<PublicKey>

    /**
     * Returns list of keys satisfying the filter condition. All filter values are combined as AND.
     *
     * @param skip the response paging information, number of records to skip.
     * @param take the response paging information, number of records to return, the actual number may be less than
     * requested.
     * @param orderBy the order by.
     * @param category the HSM's category which handles the keys.
     * @param schemeCodeName the key's signature scheme name.
     * @param alias the alias which is assigned by the tenant.
     * @param masterKeyAlias the wrapping key alias.
     * @param createdAfter specifies inclusive time after which a key was created.
     * @param createdAfter specifies inclusive time before which a key was created.
     */
    fun lookup(
        skip: Int,
        take: Int,
        orderBy: SigningKeyOrderBy,
        category: String?,
        schemeCodeName: String?,
        alias: String?,
        masterKeyAlias: String?,
        createdAfter: Instant?,
        createdBefore: Instant?
    ): Collection<SigningCachedKey>

    /**
     * Returns list of keys for provided key ids.
     *
     * @param ids The list of the key ids to look up for, the maximum number of items is 20.
     */
    fun lookup(
        ids: List<String>
    ): Collection<SigningCachedKey>
}