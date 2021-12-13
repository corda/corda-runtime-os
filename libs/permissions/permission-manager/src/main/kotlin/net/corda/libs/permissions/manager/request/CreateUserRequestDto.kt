package net.corda.libs.permissions.manager.request

import java.time.Instant

/**
 * Request object for creating a User in the permission system.
 */
data class CreateUserRequestDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,
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
    val parentGroup: String?,
)