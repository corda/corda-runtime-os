package net.corda.libs.permissions.endpoints.v1.role

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleRequestType
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
    @HttpRpcPOST(description = "Create a Role", path = "createRole")
    fun createRole(
        @HttpRpcRequestBodyParameter(description = "Details of the role to be created", required = true)
        createRoleRequestType: CreateRoleRequestType
    ): RoleResponseType

    /**
     * Get a role by its name in the RBAC permission system.
     */
    @HttpRpcGET(description = "Get a Role by name", path = "")
    fun getRole(
        @HttpRpcQueryParameter(name = "name", description = "Name of the role to be returned.", required = true)
        name: String
    ): RoleResponseType
}