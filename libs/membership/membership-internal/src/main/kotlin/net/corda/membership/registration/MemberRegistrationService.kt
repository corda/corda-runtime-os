package net.corda.membership.registration

import net.corda.virtualnode.HoldingIdentity

enum class MembershipRequestRegistrationResult {
    /**
     * Registration request got submitted to the MGM successfully.
     */
    SUBMITTED,

    /**
     * Something went wrong and registration request was not sent to the MGM.
     */
    NOT_SUBMITTED
}

/**
 * Handles the registration process on the member side.
 */
interface MemberRegistrationService {
    /**
     * Creates the registration request and submits it towards the MGM.
     * This is the first step to take for a virtual node to become a fully
     * qualified member within a membership group.
     *
     * @param member The holding identity of the virtual node requesting registration.
     *
     * @return The status of the registration request. NOT_SUBMITTED is returned when
     * something went wrong during creating the request, or we are unable to find the MGM details.
     */
    fun register(member: HoldingIdentity): MembershipRequestRegistrationResult
}