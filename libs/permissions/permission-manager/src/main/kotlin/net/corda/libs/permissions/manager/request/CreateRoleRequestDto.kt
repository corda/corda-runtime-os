package net.corda.libs.permissions.manager.request

/**
 * Request object for creating a Role in the permission system.
 */
data class CreateRoleRequestDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,
    /**
     * The ID of the virtual node in which to create the User.
     */
    val virtualNodeId: String?,
    /**
     * Name of the Role.
     */
    val roleName: String
)