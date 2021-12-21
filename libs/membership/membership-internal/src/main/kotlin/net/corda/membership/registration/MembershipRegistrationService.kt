package net.corda.membership.registration

import net.corda.membership.CpiVersion

enum class MembershipRegistrationResult {
    /**
     * Registration got submitted to the MGM successfully.
     */
    SUBMITTED,

    /**
     * Registration request is not yet processed and received by MGM.
     */
    INCOMPLETE_REGISTRATION
}

interface MembershipRegistrationService {
    val cpiVersion: CpiVersion
    val platformVersion: Int
    /**
     *  Used to register a member within a membership group in order to become a fully qualified member.
     *
     */
    fun register(): MembershipRegistrationResult

    /**
     * Check the status of submitting the registration request.
     */
    fun checkRegistrationStatus(): MembershipRegistrationResult
}