package net.corda.libs.permissions.manager.request

/**
 * Request object for assigning a Property to a User in the permission system.
 */
data class AddPropertyToUserRequestDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,
    /**
     * Login name of the User to change.
     */
    val loginName: String,
    /**
     * Property to be added.
     */
    val properties: Map<String, String>
)
