package net.corda.membership.httprpc.v1.types.response

data class MGMGroupPolicyInfo(
    val status: String,
    val groupPolicy: String
)

/**
 * Registration result returned after calling [MemberRegistrationService.register].
 *
 * @property outcome Enum value representing the outcome of the registration.
 * @property message Additional information, like reason for failed registration submission.
 */
data class MGMGroupPolicyResult(val outcome: Boolean, val message: String? = null)