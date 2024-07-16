package net.corda.libs.permissions.manager.request

/**
 * Request object for getting properties of a user in the permission system.
 */
data class GetUserPropertiesRequestDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,
    /**
     * Login name of the User to get.
     */
    val loginName: String,
)
