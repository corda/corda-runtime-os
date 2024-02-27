package net.corda.libs.permissions.manager.request

import net.corda.libs.permissions.manager.common.PermissionTypeDto

data class QueryPermissionsRequestDto(
    val limit: Int,
    val permissionType: PermissionTypeDto,
    val groupVisibility: String?,
    val virtualNode: String?,
    val permissionStringPrefix: String?
)
