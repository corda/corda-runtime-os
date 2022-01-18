package net.corda.libs.permissions.endpoints.v1.permission

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.libs.permissions.endpoints.v1.schema.CreatePermissionType
import net.corda.libs.permissions.endpoints.v1.schema.PermissionResponseType

/**
 * Permission endpoint exposes HTTP endpoints for management of Permissions in the RBAC permission system.
 */
@HttpRpcResource(
    name = "PermissionEndpoint",
    description = "Permissions Management APIs",
    path = "permission"
)
interface PermissionEndpoint : RpcOps {

    /**
     * Create a permission entity in the RBAC permission system.
     */
    @HttpRpcPOST(description = "Create a Permission", path = "createPermission")
    fun createPermission(
        @HttpRpcRequestBodyParameter(description = "Details of the permission to be created", required = true)
        createPermissionType: CreatePermissionType
    ): PermissionResponseType

    /**
     * Get a permission by its identifier in the RBAC permission system.
     */
    @HttpRpcGET(description = "Get a Permission by its ID", path = "")
    fun getPermission(
        @HttpRpcQueryParameter(name = "id", description = "ID of the permission to be returned.", required = true)
        id: String
    ): PermissionResponseType
}