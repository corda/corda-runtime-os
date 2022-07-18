package net.corda.membership.client

import net.corda.lifecycle.Lifecycle
import net.corda.membership.client.dto.MGMGenerateGroupPolicyResponseDto

/**
 * The MGM ops client to perform group operations.
 */
interface MGMOpsClient : Lifecycle {

    /**
     * Generates the Group Policy file to be used to register a new member to the MGM
     *
     * @param holdingIdentityId The ID of the holding identity to be checked.
     * @return [MGMGenerateGroupPolicyResponseDto] Generated Group Policy Response.
     */
    fun generateGroupPolicy(holdingIdentityId: String): MGMGenerateGroupPolicyResponseDto
}