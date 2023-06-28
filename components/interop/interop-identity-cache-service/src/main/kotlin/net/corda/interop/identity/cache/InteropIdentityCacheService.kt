package net.corda.interop.identity.cache

import net.corda.lifecycle.Lifecycle
import net.corda.data.interop.InteropAliasIdentity


interface InteropIdentityCacheService : Lifecycle {
    /**
     * Gets all interop alias identities for a given holding identity.
     *
     * @param shortHash Short hash of the real holding identity to get alias identities for.
     * @return Map of interop group UUID strings to interop alias identity objects.
     */
    fun getAliasIdentities(shortHash: String): Map<String, InteropAliasIdentity>

    /**
     * Add an alias identity to the cache.
     *
     * @param shortHash Short hash of the real holding identity to add alias identity to.
     * @param aliasIdentity New alias identity to add to the cache.
     */
    fun putAliasIdentity(shortHash: String, aliasIdentity: InteropAliasIdentity)

    /**
     * Remove an alias identity from the cache.
     *
     * @param shortHash Short hash of the real holding identity to remove from the cache.
     * @param aliasIdentity Alias identity to remove from the cache.
     */
    fun removeAliasIdentity(shortHash: String, aliasIdentity: InteropAliasIdentity)
}
