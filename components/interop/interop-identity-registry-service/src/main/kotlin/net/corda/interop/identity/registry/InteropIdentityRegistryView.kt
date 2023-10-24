package net.corda.interop.identity.registry

import java.util.UUID
import net.corda.crypto.core.ShortHash
import net.corda.interop.core.InteropIdentity
import net.corda.v5.application.interop.facade.FacadeId


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
     * Get identities within the view with a specific group ID.
     *
     * @param groupId Group ID of the interop identities to return.
     * @return Set of [InteropIdentity] objects with the specified group ID.
     */
    fun getIdentitiesByGroupId(groupId: UUID): Set<InteropIdentity>

    /**
     * Get the identity within the view with a specific short hash.
     *
     * @param shortHash Short hash of the interop identity to return.
     * @return [InteropIdentity] object with the specified short hash or null if no such identity exists.
     */
    fun getIdentityWithShortHash(shortHash: ShortHash): InteropIdentity?

    /**
     * Get identities within the view with a specific application name.
     *
     * @param applicationName Application name of the interop identities to return.
     * @return Set of [InteropIdentity] objects with the specified application name.
     */
    fun getIdentitiesByApplicationName(applicationName: String): Set<InteropIdentity>

    /**
     * Get interop identity with the matching application name.
     *
     * @param applicationName Application name of identity to return.
     * @return [InteropIdentity] with the specified application name or null if no such identity exists.
     */
    fun getIdentityWithApplicationName(applicationName: String): InteropIdentity?

    /**
     * Get identities within the view with the specified facade ID.
     *
     * @param facadeId Facade ID for the query.
     * @return Set of [InteropIdentity] objects with the specified facade ID.
     */
    fun getIdentitiesByFacadeId(facadeId: FacadeId): Set<InteropIdentity>

    /**
     * Get owned identities within the view with the specified interop group.
     *
     * @param groupId Group ID of the interop group to return owned identities from.
     * @return Set of [InteropIdentity] objects with the specified group ID.
     */
    fun getOwnedIdentities(groupId: UUID): Set<InteropIdentity>

    /**
     * Get owned identity within the view for the specified interop group.
     *
     * @param groupId Group ID of the interop group to get the owned identity of.
     * @return The owned [InteropIdentity] in the specified group if there is one, null otherwise.
     * @throws InteropIdentityRegistryStateError if multiple owned identities are present in the registry.
     */
    fun getOwnedIdentity(groupId: UUID): InteropIdentity?
}
