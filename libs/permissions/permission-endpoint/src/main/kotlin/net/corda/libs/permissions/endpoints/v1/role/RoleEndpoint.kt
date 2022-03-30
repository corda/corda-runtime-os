package net.corda.libs.permissions.endpoints.v1.role

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPUT
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import net.corda.libs.permissions.endpoints.v1.role.types.RoleResponseType

/**
 * Role endpoint exposes HTTP endpoints for management of Roles in the RBAC permission system.
 */
@HttpRpcResource(
    name = "RoleEndpoint",
    description = "Role Management APIs",
    path = "role"
)
interface RoleEndpoint : RpcOps {

    /**
     * Create a role in the RBAC permission system.
     */
    @HttpRpcPOST(description = "Create a Role")
    fun createRole(
        @HttpRpcRequestBodyParameter(description = "Details of the role to be created")
        createRoleType: CreateRoleType
    ): RoleResponseType

    /**
     * Get a role by its identifier in the RBAC permission system.
     */
    @HttpRpcGET(path = "{id}", description = "Get a Role by its ID")
    fun getRole(
        @HttpRpcPathParameter(description = "ID of the role to be returned.")
        id: String
    ): RoleResponseType

    /**
     * Associates a role with a permission
     */
    @HttpRpcPUT(path = "addPermission", description = "Add a permission to a role")
    fun addPermission(
        @HttpRpcRequestBodyParameter(description = "Identifier for an existing role")
        roleId: String,
        @HttpRpcRequestBodyParameter(description = "Identifier for an existing permission")
        permissionId: String
    ): RoleResponseType

    /**
     * Removes Association between a role and a permission
     */
    @HttpRpcPUT(path = "removePermission", description = "Removes a permission from a role")
    fun removePermission(
        @HttpRpcRequestBodyParameter(description = "Identifier for an existing role")
        roleId: String,
        @HttpRpcRequestBodyParameter(description = "Identifier for an existing permission")
        permissionId: String
    ): RoleResponseType
}