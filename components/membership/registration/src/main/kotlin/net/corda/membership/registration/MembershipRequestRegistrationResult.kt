package net.corda.membership.registration

/**
 * Registration result returned after calling [MemberRegistrationService.register].
 *
 * @property outcome Enum value representing the outcome of the registration.
 * @property message Additional information, like reason for failed registration submission.
 */
data class MembershipRequestRegistrationResult(val outcome: MembershipRequestRegistrationOutcome, val message: String? = null)