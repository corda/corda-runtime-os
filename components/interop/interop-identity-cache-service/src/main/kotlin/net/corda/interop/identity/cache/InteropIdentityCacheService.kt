package net.corda.interop.identity.cache

import net.corda.lifecycle.Lifecycle
import net.corda.data.interop.InteropAliasIdentity


interface InteropIdentityCacheService : Lifecycle {
    /**
     * Gets all interop identities for a given holding identity.
     *
     * @param shortHash Short hash of the real holding identity to get alias identities for.
     * @return Map of interop group UUID strings to interop alias identity objects.
     */
    fun getInteropIdentities(shortHash: String): Map<String, InteropAliasIdentity>

    /**
     * Add an interop identity to the cache.
     *
     * @param shortHash Short hash of the real holding identity to add alias identity to.
     * @param identity New alias identity to add to the cache.
     */
    fun putInteropIdentities(shortHash: String, identity: InteropAliasIdentity)

    /**
     * Remove an interop identity from the cache.
     *
     * @param shortHash Short hash of the real holding identity to remove from the cache.
     * @param identity Alias identity to remove from the cache.
     */
    fun removeInteropIdentity(shortHash: String, identity: InteropAliasIdentity)
}
