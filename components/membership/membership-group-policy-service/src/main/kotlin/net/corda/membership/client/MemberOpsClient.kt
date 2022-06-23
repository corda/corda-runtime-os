package net.corda.membership.client

import net.corda.lifecycle.Lifecycle
import net.corda.membership.client.dto.MemberRegistrationRequestDto
import net.corda.membership.client.dto.RegistrationRequestProgressDto

/**
 * The member ops client to perform group operations.
 */
interface MemberOpsClient : Lifecycle {

    /**
     * Starts the registration process for a member.
     *
     * @param memberRegistrationRequest Data necessary to include in order to initiate registration.
     * @return [RegistrationRequestProgress] to indicate the status of the request at time of submission.
     */
    fun startRegistration(memberRegistrationRequest: MemberRegistrationRequestDto): RegistrationRequestProgressDto

    /**
     * Checks the latest known status of registration based on a member's own local data and without
     * outwards communication.
     *
     * @param holdingIdentityId The ID of the holding identity to be checked.
     * @return [RegistrationRequestProgress] to indicate the last known status of the registration request based on
     * local member data.
     */
    fun checkRegistrationProgress(holdingIdentityId: String): RegistrationRequestProgressDto
}