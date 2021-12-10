package net.corda.libs.permissions.endpoints.v1.role.types

import java.time.Instant
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionResponseType

/**
 * Response type representing a Role to be returned to the caller.
 */
data class RoleResponseType(

    /**
     * Id of the Role.
     */
    val id: String,

    /**
     * Version of the Role.
     */
    val version: Int,

    /**
     * Time the Role was last updated.
     */
    val updateTimestamp: Instant,

    /**
     * Name of the Role.
     */
    val roleName: String,

    /**
     * Group visibility of the Role.
     */
    val groupVisibility: String?,

    /**
     * List of permissions the Role has.
     */
    val permissions: List<PermissionResponseType>
)