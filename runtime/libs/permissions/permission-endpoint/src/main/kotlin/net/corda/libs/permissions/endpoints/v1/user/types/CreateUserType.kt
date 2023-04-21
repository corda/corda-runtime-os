package net.corda.libs.permissions.endpoints.v1.user.types

import net.corda.rest.exception.InvalidInputDataException
import net.corda.rbac.schema.RbacKeys.USER_REGEX
import java.time.Instant
import java.util.UUID

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
        val errors = mutableMapOf<String, String>()

        var errCount = 0
        fun nextErrKey() : String = "Error #${++errCount}"

        if (fullName.length > 255) {
            errors[nextErrKey()] = "Full name exceed maximum length of 255."
        }

        if (fullName.isBlank()) {
            errors[nextErrKey()] = "Full name must not be blank."
        }

        "a-zA-Z0-9.@\\-#' ".let {
            val regEx = Regex("[$it]*")
            if (!regEx.matches(fullName)) {
                errors[nextErrKey()] = "Full name contains invalid characters. Allowed characters are: '$it'."
            }
        }

        if (loginName.length > 255) {
            errors[nextErrKey()] = "Login name exceed maximum length of 255."
        }

        if (loginName.isBlank()) {
            errors[nextErrKey()] = "Login name must not be blank."
        }

        USER_REGEX.let {
            val regEx = Regex(it)
            if (!regEx.matches(loginName)) {
                errors[nextErrKey()] = "Login name contains invalid characters. Correct pattern is: '$it'."
            }
        }

        if (initialPassword != null) {
            if (initialPassword.length < 5) {
                errors[nextErrKey()] = "Password is too short. Minimum length is 5."
            }

            if (initialPassword.length > 255) {
                errors[nextErrKey()] = "Password exceed maximum length of 255."
            }
        }

        if (parentGroup != null) {
            if (parentGroup.length > 36) {
                errors[nextErrKey()] = "Parent group id exceed maximum length of 36."
            }

            try {
                UUID.fromString(parentGroup)
            } catch (ex: Exception) {
                errors[nextErrKey()] = ex.message ?: "Unable to parse parent group '$parentGroup' into UUID."
            }
        }

        if (errors.isNotEmpty()) {
            throw InvalidInputDataException("Invalid input data for user creation.", errors)
        }
    }
}