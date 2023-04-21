package net.corda.libs.permissions.manager.request

/**
 * Request object for getting a User in the permission system.
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