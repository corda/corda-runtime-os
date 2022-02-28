package net.corda.membership.httprpc.v1.types.request

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