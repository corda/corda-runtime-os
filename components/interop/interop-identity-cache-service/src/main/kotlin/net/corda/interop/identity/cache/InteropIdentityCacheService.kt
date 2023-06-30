package net.corda.interop.identity.cache

import net.corda.lifecycle.Lifecycle
import net.corda.data.interop.InteropIdentity


interface InteropIdentityCacheService : Lifecycle {
    /**
     * Gets all interop identities for a given holding identity.
     *
     * @param shortHash Short hash of the real holding identity to get interop identities for.
     * @return Map of interop group UUID strings to interop identity objects.
     */
    fun getInteropIdentities(shortHash: String): Map<String, InteropIdentity>

    /**
     * Add an interop identity to the cache.
     *
     * @param shortHash Short hash of the real holding identity to add interop identity to.
     * @param identity New interop identity to add to the cache.
     */
    fun putInteropIdentities(shortHash: String, identity: InteropIdentity)

    /**
     * Remove an interop identity from the cache.
     *
     * @param shortHash Short hash of the real holding identity to remove interop identity from.
     * @param identity Interop identity to remove from the cache.
     */
    fun removeInteropIdentity(shortHash: String, identity: InteropIdentity)
}
