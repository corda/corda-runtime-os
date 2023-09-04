package net.corda.interop.identity.registry

import net.corda.crypto.core.ShortHash
import net.corda.interop.core.InteropIdentity
import java.util.*


/**
 * This class represents the state of the interop registry visible to a specific virtual node.
 */
interface InteropIdentityRegistryView {
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
    fun getIdentitiesByGroupId(): Map<UUID, Set<InteropIdentity>>

    /**
     * Get identities within the view as a map with the virtual node short hash as a key.
     *
     * @return Map of virtual node short hashes to sets of [InteropIdentity] objects.
     */
    fun getIdentitiesByVirtualNode(): Map<ShortHash, Set<InteropIdentity>>

    /**
     * Get identities within the view as a map with the interop identity short hash as a key.
     *
     * @return Map of interop identity short hashes to sets of [InteropIdentity] objects.
     */
    fun getIdentitiesByShortHash(): Map<ShortHash, InteropIdentity>

    /**
     * Get identities within the view as a map with the interop identity application name as a key.
     *
     * @return Map of interop identity application names to [InteropIdentity] objects.
     */
    fun getIdentitiesByApplicationName(): Map<String, InteropIdentity>

    /**
     * Get identities within the view as a map with the FacadeId as the key and a set of InterOpIdentities that
     * implement those facades as the value.
     *
     * @return Map of facade IDs to sets of [InteropIdentity] objects.
     */
    fun getIdentitiesByFacadeId(): Map<String, Set<InteropIdentity>>


    /**
     * Get interop identities owned by the owning virtual node of this view by group ID.
     *
     * @return Map of group IDs to [InteropIdentity] objects.
     */
    fun getOwnedIdentities(): Map<UUID, InteropIdentity>
}
