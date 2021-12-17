package net.corda.libs.permissions.endpoints.v1.permission.types

/**
 * Request type for creating a Permission in the permission system.
 */
data class CreatePermissionType(

    /**
     * Defines whether this is an ALLOW or DENY type of permission.
     */
    val permissionType: PermissionTypeEnum,

    /**
     * Machine-parseable string representing an individual permission. It can be any arbitrary string as long as the authorization code can
     * make use of it in the context of user permission matching.
     */
    val permissionString: String,

    /**
     * Optional group visibility identifier of the Permission.
     */
    val groupVisibility: String?,

    /**
     * Optional identifier of the virtual node within which the physical node permission applies to.
     */
    val virtualNode: String?
)