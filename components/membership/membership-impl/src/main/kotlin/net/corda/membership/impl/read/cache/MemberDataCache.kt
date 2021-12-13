package net.corda.membership.impl.read.cache

import net.corda.v5.membership.identity.MemberX500Name

/**
 * Interface for classes which are to be used as caches for group member data.
 * This assumes data is stored and retrieved based on the holding identity of the member owning the data.
 */
interface MemberDataCache<T> {
    /**
     * Get a cached instance of [T], or null if no cached value exists.
     * Cache lookups are done using a member's holding identity.
     *
     * @param groupId The membership group ID as a [String].
     * @param memberX500Name The [MemberX500Name] of the member who owns the cached data.
     */
    fun get(groupId: String, memberX500Name: MemberX500Name): T?

    /**
     * Add a new, or update an existing cache entry which is an instance of [T].
     * The member's holding identity is used for cache storage.
     *
     * @param groupId The membership group ID as a [String].
     * @param memberX500Name The [MemberX500Name] of the member who owns the cached data.
     * @param data The data to cache
     */
    fun put(groupId: String, memberX500Name: MemberX500Name, data: T)
}

/**
 * An extension of [MemberDataCache] which is used when the data to be cached for a member is a list of data.
 */
interface MemberDataListCache<T> : MemberDataCache<List<T>>