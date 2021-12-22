package net.corda.membership.registration

enum class MembershipRegistrationResult {
    /**
     * Registration got submitted to the MGM successfully.
     */
    SUBMITTED,

    /**
     * Registration request is not yet created, sent to and received by MGM.
     */
    INCOMPLETE_REGISTRATION
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
     * @param memberId Hash-based ID for identifying the virtual node requesting registration.
     */
    fun register(memberId: String)

    /**
     * Check the status of the registration request submission.
     *
     * @param memberId Hash-based ID for identifying the virtual node which registration status we want to check.
     *
     * @return The status of the registration request. INCOMPLETE_REGISTRATION is returned when
     * no request was submitted.
     */
    fun checkRegistrationStatus(memberId: String): MembershipRegistrationResult
}