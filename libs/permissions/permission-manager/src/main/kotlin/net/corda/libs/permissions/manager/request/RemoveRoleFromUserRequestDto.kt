package net.corda.libs.permissions.manager.request

/**
 * Request object for disassociating a Role from a User in the permission system.
 */
data class RemoveRoleFromUserRequestDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,
    /**
     * User loginName to have role disassociated.
     */
    val loginName: String,
    /**
     * Name of role to be disassociated.
     */
    val roleName: String,
)