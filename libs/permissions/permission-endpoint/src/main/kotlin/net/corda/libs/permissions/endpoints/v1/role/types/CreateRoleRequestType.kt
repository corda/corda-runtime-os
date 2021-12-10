package net.corda.libs.permissions.endpoints.v1.role.types

/**
 * Request type for creating a Role in the permission system.
 */
data class CreateRoleRequestType(

    /**
     * Name of the Role.
     */
    val roleName: String,

    /**
     * Optional group visibility of the Role.
     */
    val groupVisibility: String?
)