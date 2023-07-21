package net.corda.interop.identity.cache

import net.corda.lifecycle.Lifecycle


interface InteropIdentityCacheService : Lifecycle {
    /**
     * Returns an object representing a given virtual nodes view of the cache.
     *
     * @param shortHash Short hash of the virtual node to get the view for.
     * @return A cache view object containing interop identities visible to that interop identity.
     */
    fun getVirtualNodeCacheView(shortHash: String): InteropIdentityCacheView
}
