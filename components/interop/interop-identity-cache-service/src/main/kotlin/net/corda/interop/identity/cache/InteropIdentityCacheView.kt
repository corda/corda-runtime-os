package net.corda.interop.identity.cache

import net.corda.interop.core.InteropIdentity


/**
 * This class represents the state of the interop configuration visible to a specific virtual node.
 */
interface InteropIdentityCacheView {
    /**
     * Get all identities within the view.
     *
     * @return Set of [InteropIdentity] objects.
     */
    fun getIdentities(): Set<InteropIdentity>

    /**
     * Get identities within the view as a map with the group ID as a key.
     *
     * @return Map of group ID strings to sets of [InteropIdentity] objects.
     */
    fun getIdentitiesByGroupId(): Map<String, Set<InteropIdentity>>

    /**
     * Get identities within the view as a map with the holding identity short hash as a key.
     *
     * @return Map of holding identity short hashes to sets of [InteropIdentity] objects.
     */
    fun getIdentitiesByHoldingIdentity(): Map<String, Set<InteropIdentity>>

    /**
     * Get identities within the view as a map with the interop identity short hash as a key.
     *
     * @return Map of interop identity short hashes to sets of [InteropIdentity] objects.
     */
    fun getIdentitiesByShortHash(): Map<String, Set<InteropIdentity>>
}
