package net.corda.libs.permissions.manager.request

/**
 * Request object for listing Users in the permission system.
 */
data class GetUserRequestDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,
    /**
     * The ID of the virtual node in which to create the User.
     */
    val virtualNodeId: String?,
    /**
     * User login name to be found.
     */
    val loginName: String
)