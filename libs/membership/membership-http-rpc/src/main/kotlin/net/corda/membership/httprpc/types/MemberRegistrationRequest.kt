package net.corda.membership.httprpc.types

/**
 * Request sent during member registration.
 *
 * @param virtualNodeId the ID of the virtual node the member is running on.
 * @param action the action to take during registration.
 */
data class MemberRegistrationRequest(
    val virtualNodeId: String,
    val action: RegistrationAction
)

/**
 * Possible actions during registration.
 *
 * @param action the action to take during registration.
 */
enum class RegistrationAction(private val action: String) {
    REQUEST_JOIN("requestJoin");

    override fun toString(): String {
        return action
    }
}