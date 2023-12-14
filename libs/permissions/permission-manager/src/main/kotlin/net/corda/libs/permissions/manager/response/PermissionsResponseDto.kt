package net.corda.libs.permissions.manager.response

/**
 * Response object containing information for a Permission entity.
 */
data class PermissionsResponseDto(
    val createdPermissionIds: Set<String>,
    val roleIds: Set<String>
)
