package net.corda.libs.permissions.manager.request

/**
 * Request object for getting a Role in the permission system.
 */
data class GetRoleRequestDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,
    /**
     * ID of the role to be found.
     */
    val roleId: String
)