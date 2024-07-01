package net.corda.libs.permissions.manager.request

/**
 * Request object for assigning a Role to a User in the permission system.
 */
data class AddRoleToGroupRequestDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,
    /**
     * ID of the Group to change.
     */
    val groupId: String,
    /**
     * ID of role to be associated.
     */
    val roleId: String,
)
