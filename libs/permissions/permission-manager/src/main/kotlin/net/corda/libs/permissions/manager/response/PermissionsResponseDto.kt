package net.corda.libs.permissions.manager.response

import net.corda.libs.permissions.manager.common.PermissionTypeDto
import java.time.Instant

/**
 * Response object containing information for a Permission entity.
 */
data class PermissionsResponseDto(
    val createdPermissionIds: Set<String>,
    val roleIds: Set<String>
)