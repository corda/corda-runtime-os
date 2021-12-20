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
     * Defines whether this is an ALLOW or DENY type of permission.
     */
    val permissionType: PermissionType,

    /**
     * Machine-parseable string representing an individual permission. It can be any arbitrary string as long as the authorization code can
     * make use of it in the context of user permission matching.
     */
    val permissionString: String
)