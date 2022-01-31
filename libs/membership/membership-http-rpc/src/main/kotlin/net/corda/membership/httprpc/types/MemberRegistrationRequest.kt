package net.corda.membership.httprpc.types

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

/**
 * Possible actions during registration.
 *
 * @param action The action to take during registration.
 */
enum class RegistrationAction(private val action: String) {
    REQUEST_JOIN("requestJoin");

    override fun toString(): String {
        return action
    }
}