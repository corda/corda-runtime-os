package net.corda.membership.impl.registration.verifiers

import net.corda.membership.lib.MemberInfoExtension.Companion.CUSTOM_KEY_PREFIX

internal class RegistrationContextCustomFieldsVerifier {

    private companion object {
        const val MAX_VALUE_LENGTH = 800
        const val MAX_KEY_LENGTH = 128
        const val MAX_CUSTOM_FIELDS = 100
    }

    fun verify(context: Map<String, String?>): Result {
        val customFields = context.filter { it.key.startsWith("$CUSTOM_KEY_PREFIX.") }
        if (customFields.size > MAX_CUSTOM_FIELDS) {
            return Result.Failure(
                "The number of custom fields (${customFields.size}) in the registration context is larger than " +
                    "the maximum allowed ($MAX_CUSTOM_FIELDS)."
            )
        }

        var errorMessages = ""
        customFields.forEach {
            if (it.key.length > MAX_KEY_LENGTH) {
                errorMessages += "The key: ${it.key} has too many characters (${it.key.length}). Maximum of $MAX_KEY_LENGTH characters " +
                    "allowed.\n"
            }
            if ((it.value?.length ?: 0) > MAX_VALUE_LENGTH) {
                errorMessages += "The key: ${it.key} has a value which has too many characters (${it.value?.length}). Maximum of" +
                    " $MAX_VALUE_LENGTH characters allowed.\n"
            }
        }
        return if (errorMessages.isEmpty()) {
            Result.Success
        } else {
            Result.Failure("Failed to validate the registration context with the following errors:\n$errorMessages")
        }
    }

    sealed class Result {
        object Success : Result()
        data class Failure(val reason: String) : Result()
    }
}
