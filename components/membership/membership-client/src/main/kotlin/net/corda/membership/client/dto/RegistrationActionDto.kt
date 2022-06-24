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

    companion object {
        private val lookup = HashMap<String, RegistrationActionDto>()

        init {
            RegistrationActionDto.values().map { lookup.put(it.action, it) }
        }

        fun getFromValue(value: String):RegistrationActionDto {
            return lookup[value]!!
        }
    }
}