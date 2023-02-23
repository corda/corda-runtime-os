package net.corda.libs.permissions.endpoints.v1.permission

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.annotations.RestQueryParameter
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.response.ResponseEntity
import net.corda.libs.permissions.endpoints.v1.permission.types.BulkCreatePermissionsRequestType
import net.corda.libs.permissions.endpoints.v1.permission.types.BulkCreatePermissionsResponseType
import net.corda.libs.permissions.endpoints.v1.permission.types.CreatePermissionType
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionResponseType

/**
 * Permission endpoint exposes functionality for management of Permissions in the RBAC permission system.
 */
@HttpRestResource(
    name = "RBAC Permission API",
    description = "The RBAC Permission API consists of a number of endpoints enabling permissions management in the " +
            "RBAC (role-based access control) permission system. You can get details of specified permissions " +
            "and create new permissions.",
    path = "permission"
)
interface PermissionEndpoint : RestResource {

    /**
     * Create a permission entity in the RBAC permission system.
     */
    @HttpPOST(description = "This method creates a new permission.", responseDescription = """
        id: The server-side generated ID of the new permission
        permissionType: Defines whether this is an ALLOW or DENY type of permission
        permissionString: A machine-parseable string representing an individual permission; 
            it can be any arbitrary string as long as the authorization code can make use of it in the context of user 
            permission matching
        groupVisibility: An optional group visibility identifier of the permission
        virtualNode: An optional identifier of the virtual node to which the physical node permission applies
        version: The version number of the permission; a value of 0 is assigned to a newly-created permission
        updateTimestamp: The server-side timestamp showing when the permission was created
    """)
    fun createPermission(
        @ClientRequestBodyParameter(
            description = """
            Details of the permission to be created. 
            permissionType: Defines whether this is an ALLOW or DENY type of permission
            permissionString: A machine-parseable string representing an individual permission; 
                it can be any arbitrary string as long as the authorization code can make use of it in the context of user 
                permission matching
            groupVisibility: An optional group visibility identifier of the permission
            virtualNode: An optional identifier of the virtual node to which the physical node permission applies""")
        createPermissionType: CreatePermissionType
    ): ResponseEntity<PermissionResponseType>

    /**
     * Get a permission by its identifier in the RBAC permission system.
     */
    @HttpGET(path = "{id}", description = "This method returns the permission associated with the specified ID.",
        responseDescription = """
        id: The server-side generated ID of the new permission
        permissionType: Defines whether this is an ALLOW or DENY type of permission
        permissionString: A machine-parseable string representing an individual permission; 
            it can be any arbitrary string as long as the authorization code can make use of it in the context of user 
            permission matching
        groupVisibility: An optional group visibility identifier of the permission
        virtualNode: An optional identifier of the virtual node to which the physical node permission applies
        version: The version number of the permission; a value of 0 is assigned to a newly-created permission
        updateTimestamp: The server-side timestamp showing when the permission was created""")
    fun getPermission(
        @RestPathParameter(description = "ID of the permission to be returned.")
        id: String
    ): PermissionResponseType

    @HttpGET(
        description = "This method returns permissions which satisfy supplied query criteria.",
        responseDescription = "Permissions which satisfy supplied query criteria")
    fun queryPermissions(
        @RestQueryParameter(description = "The maximum number of results to return. " +
                "The value must be in the range [1..1000].")
        limit: Int,
        @RestQueryParameter(description = "The permission type to be returned.")
        permissionType: String,
        @RestQueryParameter(description = "Optional group visibility for a permission.", required = false)
        groupVisibility: String? = null,
        @RestQueryParameter(description = "Optional virtual node the permissions apply to.", required = false)
        virtualNode: String? = null,
        @RestQueryParameter(
            description = "Optional permission string prefix for permissions to be located.", required = false)
        permissionStringPrefix: String? = null
        ): List<PermissionResponseType>

    /**
     * Create a set of permissions in the RBAC permission system and optionally assigns them to existing roles.
     */
    @HttpPOST(path = "bulk",
        description = "This method creates a set of permissions and optionally assigns them to the existing roles.",
        responseDescription = "A set of identifiers for permissions created along with role identifiers " +
                "they were associated with.")
    fun createAndAssignPermissions(
        @ClientRequestBodyParameter(description = "The details of the permissions to be created along with existing role " +
                "identifiers newly created permissions should be associated with.")
        request: BulkCreatePermissionsRequestType
    ): ResponseEntity<BulkCreatePermissionsResponseType>
}