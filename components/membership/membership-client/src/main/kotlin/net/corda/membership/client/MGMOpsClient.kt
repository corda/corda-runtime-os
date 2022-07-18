package net.corda.membership.client

import net.corda.lifecycle.Lifecycle
import net.corda.membership.client.dto.MGMGenerateGroupPolicyResponseDto

/**
 * The member ops client to perform group operations.
 */
interface MGMOpsClient : Lifecycle {

    /**
     * Checks the latest known status of registration based on a member's own local data and without
     * outwards communication.
     *
     * @param holdingIdentityId The ID of the holding identity to be checked.
     * @return [MGMGenerateGroupPolicyResponseDto] Generated Group Policy Response.
     */
    fun generateGroupPolicy(holdingIdentityId: String): MGMGenerateGroupPolicyResponseDto
}