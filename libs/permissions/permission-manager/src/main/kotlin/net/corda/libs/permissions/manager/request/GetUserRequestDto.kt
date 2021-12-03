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
     * User login name to be found.
     */
    val loginName: String
)