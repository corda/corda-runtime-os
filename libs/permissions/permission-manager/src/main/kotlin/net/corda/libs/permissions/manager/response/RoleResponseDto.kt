package net.corda.libs.permissions.manager.response

import java.time.Instant

/**
 * Response object containing information for a Role.
 */
data class RoleResponseDto(
    val id: String,
    val version: Int,
    val lastUpdatedTimestamp: Instant,
    val roleName: String,
    val groupVisibility: String?,
    val permissions: List<PermissionAssocResponseDto>
)