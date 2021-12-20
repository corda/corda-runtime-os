package net.corda.libs.permissions.manager.response

import java.time.Instant

/**
 * Response object containing information for a Permission.
 */
data class PermissionResponseDto(
    val id: String,
    val version: Int,
    val lastUpdatedTimestamp: Instant,
    val groupVisibility: String?,
    val virtualNode: String?,
    val permissionType: String?,
    val permissionString: String?
)