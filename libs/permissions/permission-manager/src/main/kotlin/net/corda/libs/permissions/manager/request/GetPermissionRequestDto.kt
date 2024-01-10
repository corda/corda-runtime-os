package net.corda.libs.permissions.manager.request

/**
 * Request object for getting a Permission entity in the permission system.
 */
data class GetPermissionRequestDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,
    /**
     * ID of the permission to be found.
     */
    val permissionId: String
)