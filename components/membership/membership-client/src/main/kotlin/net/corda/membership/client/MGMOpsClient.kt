package net.corda.membership.client

import net.corda.lifecycle.Lifecycle

/**
 * The member ops client to perform group operations.
 */
interface MGMOpsClient : Lifecycle {

    /**
     * Checks the latest known status of registration based on a member's own local data and without
     * outwards communication.
     *
     * @param holdingIdentityId The ID of the holding identity to be checked.
     * @return [Set<Map.Entry<String, String?>>] to indicate the last known status of the registration request based on
     * local member data.
     */
    fun generateGroupPolicy(holdingIdentityId: String): Set<Map.Entry<String, String?>>
}