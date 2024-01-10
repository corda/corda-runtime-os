package net.corda.libs.permissions.endpoints.v1.user.types

import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionType

/**
 * Response type representing a summary of a single permission.
 */
data class PermissionSummaryResponseType(

    /**
     * ID of the Permission.
     */
    val id: String,

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