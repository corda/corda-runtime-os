package net.corda.libs.permissions.manager.request

/**
 * Request object for un-assigning a Role from a Group in the permission system.
 */
data class RemoveRoleFromGroupRequestDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,
    /**
     * ID of the Group to change.
     */
    val groupId: String,
    /**
     * ID of role to be unassigned.
     */
    val roleId: String,
)
