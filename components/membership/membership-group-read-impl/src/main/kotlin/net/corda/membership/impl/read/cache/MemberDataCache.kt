package net.corda.membership.impl.read.cache

import net.corda.virtualnode.HoldingIdentity
import java.util.concurrent.ConcurrentHashMap

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
     * @param holdingIdentity The [HoldingIdentity] of the member whose view on data the caller is requesting
     */
    fun get(holdingIdentity: HoldingIdentity): T?

    /**
     * Add a new, or update an existing cache entry which is an instance of [T].
     * The member's holding identity is used for cache storage.
     *
     * @param holdingIdentity The [HoldingIdentity] of the member whose view on data the caller is updating
     * @param data The data to cache
     */
    fun put(holdingIdentity: HoldingIdentity, data: T)

    /**
     * Basic member data in-memory cache implementation.
     * Data is stored as a map from group ID to map of member name to [T].
     */
    class Impl<T> : MemberDataCache<T> {

        private val cache = ConcurrentHashMap<String, T>()

        override fun get(holdingIdentity: HoldingIdentity): T? = cache[holdingIdentity.id]

        override fun put(holdingIdentity: HoldingIdentity, data: T) {
            cache[holdingIdentity.id] = data
        }
    }
}
