package net.corda.libs.permissions.manager.request

/**
 * Request object for adding a Role to a User in the permission system.
 */
data class AddRoleToUserRequestDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,
    /**
     * User loginName to have role associated.
     */
    val loginName: String,
    /**
     * Id of role to be associated.
     */
    val roleId: String,
)