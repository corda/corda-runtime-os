package net.corda.membership.client.dto

/**
 * Possible actions during registration.
 *
 * @param action The action to take during registration.
 */
enum class RegistrationActionDto(private val action: String) {
    REQUEST_JOIN("requestjoin");

    override fun toString(): String {
        return action
    }

    fun getFromValue(value: String) = when(value.lowercase()) {
        REQUEST_JOIN.toString().lowercase() -> RegistrationActionDto.REQUEST_JOIN
        else -> throw IllegalArgumentException("Unsupported registration action.")
    }
}