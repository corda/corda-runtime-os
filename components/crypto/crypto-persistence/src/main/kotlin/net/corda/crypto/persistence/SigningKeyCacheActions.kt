package net.corda.crypto.persistence

import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import java.security.PublicKey

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
    fun lookup(
        skip: Int,
        take: Int,
        orderBy: SigningKeyOrderBy,
        filter: Map<String, String>
    ): Collection<SigningCachedKey>

    /**
     * Returns list of keys for provided key ids.
     *
     * @param ids The list of the key ids to look up for, the maximum number of items is 20.
     *
     * @throws IllegalArgumentException if the number of ids exceeds 20.
     */
    fun lookup(
        ids: List<String>
    ): Collection<SigningCachedKey>
}