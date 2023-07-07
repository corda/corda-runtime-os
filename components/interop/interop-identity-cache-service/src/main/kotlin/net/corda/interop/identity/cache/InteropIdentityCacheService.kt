package net.corda.interop.identity.cache

import net.corda.lifecycle.Lifecycle


interface InteropIdentityCacheService : Lifecycle {
    /**
     * Returns an object representing a given holding identities view of the cache.
     *
     * @param shortHash Short hash of the real holding identity to get the view for.
     * @return A cache view object containing interop identities visible to that interop identity.
     */
    fun getHoldingIdentityCacheView(shortHash: String): InteropIdentityCacheView
}
