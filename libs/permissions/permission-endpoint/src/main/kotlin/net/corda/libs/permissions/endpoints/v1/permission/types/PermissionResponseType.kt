package net.corda.libs.permissions.endpoints.v1.permission.types

import java.time.Instant

/**
 * Response type representing a Permission to be returned to the caller.
 */
data class PermissionResponseType(

    /**
     * Id of the Permission.
     */
    val id: String,

    /**
     * Version of the Permission.
     */
    val version: Int,

    /**
     * Time the Permission was last updated.
     */
    val updateTimestamp: Instant,

    /**
     * Group visibility of the Permission.
     */
    val groupVisibility: String?,

    /**
     * Virtual node the permission applies to.
     */
    val virtualNode: String?,

    /**
     * Virtual node the permission applies to.
     */
    val permissionType: String?,

    /**
     * Virtual node the permission applies to.
     */
    val permissionString: String?,
)