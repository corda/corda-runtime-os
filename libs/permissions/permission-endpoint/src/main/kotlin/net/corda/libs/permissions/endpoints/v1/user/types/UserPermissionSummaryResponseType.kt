package net.corda.libs.permissions.endpoints.v1.user.types

import java.time.Instant

/**
 * Response type containing a summary of a user's permissions.
 */
data class UserPermissionSummaryResponseType(

    /**
     * Login name of the User. Acts as a unique identifier.
     */
    val loginName: String,

    /**
     * Whether the user is enabled.
     */
    val enabled: Boolean,

    /**
     * The permissions the user has.
     */
    val permissions: List<PermissionSummaryResponseType>,

    /**
     * Timestamp that the permission summary for the user was last updated.
     */
    val lastUpdateTimestamp: Instant
)