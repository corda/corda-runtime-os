package net.corda.libs.permissions.manager.request

/**
 * Request object for getting Users by a given property in the permission system.
 */
data class GetUsersByPropertyRequestDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,
    /**
     * Key of property to look for.
     */
    val propertyKey: String,
    /**
     * Value of property to match on.
     */
    val propertyValue: String
)
