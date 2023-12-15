package net.corda.libs.permissions.manager.request

/**
 * Request object for creating multiple Permission entities and assigning them to the existing roles.
 */
data class CreatePermissionsRequestDto(
    val permissionToCreate: Set<CreatePermissionRequestDto>,
    val roleIds: Set<String>
)
