package net.corda.libs.permissions.endpoints.v1.user.types

import java.time.Instant
import net.corda.v5.base.annotations.CordaSerializable

/**
 * Request type for creating a User in the permission system.
 */
@CordaSerializable
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
)