package net.corda.membership.httprpc.v1.types.request

/**
 * Request sent during member registration.
 *
 * @param virtualNodeId The ID of the virtual node the member is running on.
 * @param action The action to take during registration.
 */
data class MemberRegistrationRequest(
    val virtualNodeId: String,
    val action: RegistrationAction
)