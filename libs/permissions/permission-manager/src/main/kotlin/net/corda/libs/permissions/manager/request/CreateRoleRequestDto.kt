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
     * Name of the Role.
     */
    val roleName: String,
    /**
     * Optional group visibility for the Role.
     */
    val groupVisibility: String?
)