package net.corda.membership.impl.read.cache

import net.corda.v5.membership.identity.MemberX500Name
import java.util.Collections

/**
 * Interface for classes which are to be used as caches for group member data.
 * This assumes data is stored and retrieved based on the holding identity of the member owning the data.
 * Only one object of type [T] is cached per holding identity.
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

    /**
     * Basic member data in-memory cache implementation.
     * Data is stored as a map from group ID to map of member name to [T].
     */
    class Impl<T> : MemberDataCache<T> {

        private val cache: MutableMap<String, MutableMap<MemberX500Name, T>> =
            Collections.synchronizedMap(mutableMapOf())

        private fun getCachedData(groupId: String): MutableMap<MemberX500Name, T> =
            cache.getOrPut(groupId) { mutableMapOf() }

        override fun get(groupId: String, memberX500Name: MemberX500Name): T? =
            getCachedData(groupId)[memberX500Name]

        override fun put(groupId: String, memberX500Name: MemberX500Name, data: T) {
            getCachedData(groupId)[memberX500Name] = data
        }
    }
}
