package net.corda.membership.registration

enum class MembershipRequestRegistrationOutcome {
    /**
     * Registration request got submitted to the MGM successfully.
     */
    SUBMITTED,

    /**
     * Something went wrong and registration request was not sent to the MGM.
     */
    NOT_SUBMITTED
}