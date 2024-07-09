package net.corda.libs.permissions.manager.request

/**
 * Request object for removing a Property from a User in the permission system.
 */
data class RemovePropertyFromUserRequestDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,
    /**
     * Login name of the User to change.
     */
    val loginName: String,
    /**
     * Key of the property to be removed.
     */
    val propertyKey: String
)
