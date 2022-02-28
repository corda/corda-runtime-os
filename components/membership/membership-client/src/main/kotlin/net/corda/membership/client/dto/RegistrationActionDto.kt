package net.corda.membership.client.dto

/**
 * Possible actions during registration.
 *
 * @param action The action to take during registration.
 */
enum class RegistrationActionDto(private val action: String) {
    REQUEST_JOIN("requestJoin");

    override fun toString(): String {
        return action
    }
}