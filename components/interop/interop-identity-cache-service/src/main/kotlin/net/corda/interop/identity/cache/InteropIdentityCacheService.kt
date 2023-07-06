package net.corda.interop.identity.cache

import net.corda.lifecycle.Lifecycle


interface InteropIdentityCacheService : Lifecycle {
    /**
     * Gets all interop identities for a given holding identity.
     *
     * @param shortHash Short hash of the real holding identity to get interop identities for.
     * @return A set of interop identity cache entries visible to the holding identity.
     */
    fun getInteropIdentities(shortHash: String): Set<InteropIdentityCacheEntry>

    /**
     * Add an interop identity to the cache.
     *
     * @param shortHash Short hash of the real holding identity to add interop identity to.
     * @param identity New interop identity to add to the cache.
     */
    fun putInteropIdentity(shortHash: String, identity: InteropIdentityCacheEntry)

    /**
     * Remove an interop identity from the cache.
     *
     * @param shortHash Short hash of the real holding identity to remove interop identity from.
     * @param identity Interop identity to remove from the cache.
     */
    fun removeInteropIdentity(shortHash: String, identity: InteropIdentityCacheEntry)
}
