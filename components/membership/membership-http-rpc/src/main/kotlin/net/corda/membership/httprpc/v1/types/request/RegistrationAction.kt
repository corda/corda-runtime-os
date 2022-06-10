package net.corda.membership.httprpc.v1.types.request

/**
 * Possible actions during registration.
 *
 * @param action The action to take during registration.
 */
@Suppress("EnumNaming")
enum class RegistrationAction(private val action: String) {
    requestJoin("requestJoin");

    override fun toString(): String {
        return action
    }
}
