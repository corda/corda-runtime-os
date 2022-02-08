package net.corda.libs.permissions.endpoints.v1.user.types

import net.corda.httprpc.exception.InvalidInputDataException
import java.time.Instant
import java.util.*

/**
 * Request type for creating a User in the permission system.
 */
data class CreateUserType(

    /**
     * Full name of the User.
     */
    val fullName: String,

    /**
     * Login name of the User. Acts as a unique identifier.
     */
    val loginName: String,

    /**
     * Whether this user should be enabled or not.
     */
    val enabled: Boolean,

    /**
     * The initial password if the user is not to be set up with SSO providers.
     */
    val initialPassword: String?,

    /**
     * If the User account used basic authentication, the time in which it expires.
     */
    val passwordExpiry: Instant?,

    /**
     * The group to which the User belongs.
     */
    val parentGroup: String?
) {
    init {
        val errors = mutableListOf<String>()

        if (fullName.length > 255) {
            errors.add("Full name exceed maximum length of 255.")
        }

        "a-zA-Z0-9.@\\-# ".let {
            val regEx = Regex("[$it]*")
            if (!regEx.matches(fullName)) {
                errors.add("Full name '$fullName' contains invalid characters. Allowed characters are: '$it'.")
            }
        }

        if (loginName.length > 255) {
            errors.add("Login name exceed maximum length of 255.")
        }

        "a-zA-Z0-9.@\\-#".let {
            val regEx = Regex("[$it]*")
            if (!regEx.matches(loginName)) {
                errors.add("Login name '$loginName' contains invalid characters. Allowed characters are: '$it'.")
            }
        }

        if (initialPassword != null) {
            if (initialPassword.length > 255) {
                errors.add("Password name exceed maximum length of 255.")
            }

            "a-zA-Z0-9.@\\-#!?,".let {
                val regEx = Regex("[$it]*")
                if (!regEx.matches(initialPassword)) {
                    errors.add("Password contains invalid characters. Allowed characters are: '$it'.")
                }
            }
        }

        if (parentGroup != null) {
            if (parentGroup.length > 36) {
                errors.add("Parent group id exceed maximum length of 36.")
            }

            try {
                UUID.fromString(parentGroup)
            } catch (ex: Exception) {
                errors.add(ex.message ?: "Unable to parse parent group '$parentGroup' into UUID.")
            }
        }

        if (errors.isNotEmpty()) {
            throw InvalidInputDataException("Invalid input for user creation: " + errors.joinToString(" ") { it })
        }
    }
}