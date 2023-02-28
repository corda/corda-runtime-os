package net.corda.libs.permissions.endpoints.v1.role.types

import net.corda.rest.exception.InvalidInputDataException

/**
 * Request type for creating a Role in the permission system.
 */
data class CreateRoleType(

    /**
     * Name of the Role.
     */
    val roleName: String,

    /**
     * Optional group visibility of the Role.
     */
    val groupVisibility: String?
) {
    init {
        if (roleName.isNullOrBlank()) {
            throw InvalidInputDataException("Role name must not be null or blank.")
        }
    }
}