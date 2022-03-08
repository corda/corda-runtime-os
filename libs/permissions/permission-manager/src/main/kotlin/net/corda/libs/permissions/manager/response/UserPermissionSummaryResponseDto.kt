package net.corda.libs.permissions.manager.response

import java.time.Instant

/**
 * Response object containing a summary of permissions for a User.
 */
data class UserPermissionSummaryResponseDto(
    val loginName: String,
    val permissions: List<PermissionSummaryResponseDto>,
    val lastUpdateTimestamp: Instant
)