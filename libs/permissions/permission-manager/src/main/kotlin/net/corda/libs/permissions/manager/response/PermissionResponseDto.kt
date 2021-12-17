package net.corda.libs.permissions.manager.response

import net.corda.libs.permissions.manager.common.PermissionTypeEnum
import java.time.Instant

/**
 * Response object containing information for a Permission entity.
 */
data class PermissionResponseDto(
    val id: String,
    val version: Int,
    val lastUpdatedTimestamp: Instant,
    val groupVisibility: String?,
    val virtualNode: String?,
    val permissionType: PermissionTypeEnum,
    val permissionString: String
)