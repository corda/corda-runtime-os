package net.corda.libs.permissions.manager.request

import net.corda.libs.permissions.manager.common.PermissionTypeDto

/**
 * Request object for creating multiple Permission entities and assigning them to the existing roles.
 */
data class CreatePermissionsRequestDto(
    val permissionToCreate: Set<CreatePermissionRequestDto>,
    val roleIds: Set<String>
)