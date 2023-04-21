package net.corda.libs.permissions.manager.request

/**
 * Request object for un-assigning a Role from a User in the permission system.
 */
data class RemoveRoleFromUserRequestDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,
    /**
     * Login name of the User to change.
     */
    val loginName: String,
    /**
     * Id of role to be unassigned.
     */
    val roleId: String,
)