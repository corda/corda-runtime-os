package net.corda.membership.lib.verifiers

import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.MPV_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.CUSTOM_KEY_PREFIX

class GroupParametersUpdateVerifier {

    private companion object {
        const val MAX_KEY_LENGTH = 128
        const val MAX_VALUE_LENGTH = 800
        const val MAX_CUSTOM_FIELDS = 100
    }

    fun verify(parameters: Map<String, String>): Result {
        val customFields = parameters.filter { it.key.startsWith("$CUSTOM_KEY_PREFIX.") }
        if (customFields.size > MAX_CUSTOM_FIELDS ) {
            return Result.Failure("The number of custom fields (${customFields.size}) in the group parameters " +
                    "update is larger than the maximum allowed ($MAX_CUSTOM_FIELDS).")
        }

        var errorMessages = ""
        customFields.forEach {
            if (it.key.length > MAX_KEY_LENGTH) {
                errorMessages += "The key: ${it.key} has too many characters (${it.key.length}). Maximum of $MAX_KEY_LENGTH characters " +
                        "allowed.\n"
            }
            if (it.value.length > MAX_VALUE_LENGTH) {
                errorMessages += "The key: ${it.key} has a value which has too many characters (${it.value.length}). Maximum of" +
                        " $MAX_VALUE_LENGTH characters allowed.\n"
            }
        }
        parameters[MPV_KEY]?.let {
            if ((it.toIntOrNull() ?: -1) !in 50000..99999) {
                errorMessages += "The minimum platform version (key: $MPV_KEY) has an invalid value.\n"
            }
            it
        }
        parameters.filterNot { customFields.containsKey(it.key) || it.key == MPV_KEY }.let {
            if (it.isNotEmpty()) {
                errorMessages += "Only custom fields (with $CUSTOM_KEY_PREFIX prefix) and minimum platform version " +
                        "(key: $MPV_KEY) may be changed.\n"
            }
        }
        return if (errorMessages.isEmpty()) {
            Result.Success
        } else {
            Result.Failure("Failed to validate the group parameters update with the following errors:\n$errorMessages")
        }
    }

    sealed class Result {
        object Success: Result()
        data class Failure(val reason: String): Result()
    }
}
