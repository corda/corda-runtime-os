package net.corda.membership.client

import net.corda.lifecycle.Lifecycle
import net.corda.membership.httprpc.types.MemberRegistrationRequest
import net.corda.membership.httprpc.types.RegistrationRequestProgress

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
    fun startRegistration(memberRegistrationRequest: MemberRegistrationRequest): RegistrationRequestProgress

    /**
     * Checks the latest known status of registration based on a member's own local data and without
     * outwards communication.
     *
     * @param virtualNodeId The ID of the virtual node to be checked.
     * @return [RegistrationRequestProgress] to indicate the last known status of the registration request based on
     * local member data.
     */
    fun checkRegistrationProgress(virtualNodeId: String): RegistrationRequestProgress
}