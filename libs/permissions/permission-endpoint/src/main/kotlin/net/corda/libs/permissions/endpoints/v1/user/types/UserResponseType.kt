package net.corda.libs.permissions.endpoints.v1.user.types

import java.time.Instant

/**
 * Response type representing a User to be returned to the caller.
 */
data class UserResponseType(

    /**
     * Id of the User.
     */
    val id: String,

    /**
     * Version of the user.
     */
    val version: Int,

    /**
     * Time the user was last updated.
     */
    val updateTimestamp: Instant,

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
     * Whether this user uses SSO Authentication.
     */
    val ssoAuth: Boolean,

    /**
     * If the User account used basic authentication, the time in which it expires.
     */
    val passwordExpiry: Instant?,

    /**
     * The group to which the User belongs.
     */
    val parentGroup: String?,

    /**
     * User properties.
     */
    val properties: List<PropertyResponseType>
)