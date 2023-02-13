package net.corda.membership.client

import net.corda.lifecycle.Lifecycle
import net.corda.membership.client.dto.MemberRegistrationRequestDto
import net.corda.membership.client.dto.RegistrationRequestProgressDto
import net.corda.membership.client.dto.RegistrationRequestStatusDto
import net.corda.virtualnode.ShortHash

/**
 * The member ops client to perform group operations.
 */
interface MemberOpsClient : Lifecycle {

    /**
     * Starts the registration process for a member.
     *
     * @param memberRegistrationRequest Data necessary to include in order to initiate registration.
     * @throws CouldNotFindMemberException if the member in `holdingIdentityShortHash` can not be found.
     * @return [RegistrationRequestProgressDto] to indicate the status of the request at time of submission.
     */
    fun startRegistration(memberRegistrationRequest: MemberRegistrationRequestDto): RegistrationRequestProgressDto

    /**
     * Checks the known status of all registration based on a member's own local data and without
     * outwards communication.
     *
     * @param holdingIdentityShortHash The ID of the holding identity to be checked.
     *
     * @throws RegistrationProgressNotFoundException if there were no registration requests for given holding identity.
     * Could happen when the registration request had NOT_SUBMITTED status or if [startRegistration] wasn't called at all.
     *
     * @return [List<RegistrationRequestStatusDto>] to indicate the last known status of the registration request based on
     * local member data.
     */
    @Throws(RegistrationProgressNotFoundException::class)
    fun checkRegistrationProgress(holdingIdentityShortHash: ShortHash): List<RegistrationRequestStatusDto>

    /**
     * Checks the latest known status of a specific registration based on a member's own local data and without
     * outwards communication.
     *
     * @param holdingIdentityShortHash The ID of the holding identity to be checked.
     * @param registrationRequestId The ID of the registration request.
     *
     * @throws RegistrationProgressNotFoundException if there was no registration request for given request id. Could
     * happen when the registration request had NOT_SUBMITTED status or if [startRegistration] wasn't called at all.
     *
     * @return [RegistrationRequestStatusDto] to indicate the last known status of the registration request based on
     * local member data.
     */
    @Throws(RegistrationProgressNotFoundException::class)
    fun checkSpecificRegistrationProgress(
        holdingIdentityShortHash: ShortHash,
        registrationRequestId: String,
    ): RegistrationRequestStatusDto
}
