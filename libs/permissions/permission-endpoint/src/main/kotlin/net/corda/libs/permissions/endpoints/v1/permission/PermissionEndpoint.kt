package net.corda.libs.permissions.endpoints.v1.permission

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.httprpc.response.ResponseEntity
import net.corda.libs.permissions.endpoints.v1.permission.types.CreatePermissionType
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionResponseType

/**
 * Permission endpoint exposes functionality for management of Permissions in the RBAC permission system.
 */
@HttpRpcResource(
    name = "RBAC Permission API",
    description = "The RBAC Permission API consists of a number of endpoints enabling permissions management in the " +
            "RBAC (role-based access control) permission system. You can get details of specified permissions " +
            "and create new permissions.",
    path = "permission"
)
interface PermissionEndpoint : RpcOps {

    /**
     * Create a permission entity in the RBAC permission system.
     */
    @HttpRpcPOST(description = "This method creates a new permission.", responseDescription = """
        id - Server-side generated ID of the new permission.
        permissionType - Defines whether this is an ALLOW or DENY type of permission. 
        permissionString - Machine-parseable string representing an individual permission. 
            It can be any arbitrary string as long as the authorization code can make use of it in the context of user permission matching.
        groupVisibility - Optional group visibility identifier of the Permission.
        virtualNode - Optional identifier of the virtual node within which the physical node permission applies to.
        version - Version of the Permission. Value of 0 is assigned to a newly created permission.
        updateTimestamp - Server-side timestamp when permission was created.
    """)
    fun createPermission(
        @HttpRpcRequestBodyParameter(
            description = """Details of the permission to be created. 
            permissionType - Defines whether this is an ALLOW or DENY type of permission. 
            permissionString - Machine-parseable string representing an individual permission. 
                It can be any arbitrary string as long as the authorization code can make use of it in the context of 
                user permission matching.
            groupVisibility - Optional group visibility identifier of the Permission.
            virtualNode - Optional identifier of the virtual node within which the physical node permission applies to.""")
        createPermissionType: CreatePermissionType
    ): ResponseEntity<PermissionResponseType>

    /**
     * Get a permission by its identifier in the RBAC permission system.
     */
    @HttpRpcGET(path = "{id}", description = "This method returns the permission associated with the specified ID.",
        responseDescription = """
            id - Server-side generated ID of the new permission.
            permissionType - Defines whether this is an ALLOW or DENY type of permission. 
            permissionString - Machine-parseable string representing an individual permission. 
                It can be any arbitrary string as long as the authorization code can make use of it in the context of 
                user permission matching.
            groupVisibility - Optional group visibility identifier of the Permission.
            virtualNode - Optional identifier of the virtual node within which the physical node permission applies to.
            version - Version of the Permission.
            updateTimestamp - Server-side timestamp when permission was last modified.""")
    fun getPermission(
        @HttpRpcPathParameter(description = "ID of the permission to be returned.")
        id: String
    ): PermissionResponseType
}