package net.corda.libs.permissions.manager.request

/**
 * Request object for dissociating a Role from a User in the permission system.
 */
data class RemoveRoleFromUserRequestDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,
    /**
     * User loginName to have role dissociated.
     */
    val loginName: String,
    /**
     * Id of role to be dissociated.
     */
    val roleId: String,
)