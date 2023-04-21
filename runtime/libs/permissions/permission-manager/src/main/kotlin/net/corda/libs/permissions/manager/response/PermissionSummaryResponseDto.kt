package net.corda.libs.permissions.manager.response

import net.corda.libs.permissions.manager.common.PermissionTypeDto

/**
 * Response object containing information for a User.
 */
data class PermissionSummaryResponseDto(
    val id: String,
    val groupVisibility: String?,
    val virtualNode: String?,
    val permissionType: PermissionTypeDto,
    val permissionString: String
)