package net.corda.libs.permissions.manager.request

/**
 * Request object for assigning a Role to a User in the permission system.
 */
data class AddRoleToUserRequestDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,
    /**
     * Login name of the User to change.
     */
    val loginName: String,
    /**
     * Id of role to be associated.
     */
    val roleId: String,
)