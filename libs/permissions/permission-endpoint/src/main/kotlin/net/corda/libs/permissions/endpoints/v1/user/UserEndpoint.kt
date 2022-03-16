package net.corda.libs.permissions.endpoints.v1.user

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.libs.permissions.endpoints.v1.user.types.UserPermissionSummaryResponseType
import net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType

/**
 * User endpoint exposes HTTP endpoints for management of Users in the RBAC permission system.
 */
@HttpRpcResource(
    name = "UserEndpoint",
    description = "User Management APIs",
    path = "user"
)
interface UserEndpoint : RpcOps {

    /**
     * Create a user in the RBAC permission system.
     */
    @HttpRpcPOST(path = "createUser", description = "Create a User")
    fun createUser(
        @HttpRpcRequestBodyParameter(description = "Details of the user to be created")
        createUserType: CreateUserType
    ): UserResponseType

    /**
     * Get a user by loginName in the RBAC permission system.
     */
    @HttpRpcGET(path = "getUser", description = "Get a User by Login Name")
    fun getUser(
        @HttpRpcQueryParameter(description = "Login Name of the user to be returned.")
        loginName: String
    ): UserResponseType

    /**
     * Assign a Role to a User in the RBAC permission system.
     */
    @HttpRpcPOST(path = "addRole", description = "Assign a Role to a User")
    fun addRole(
        @HttpRpcRequestBodyParameter(description = "User login name to be changed")
        loginName: String,
        @HttpRpcRequestBodyParameter(description = "Id of the role to associate with this user")
        roleId: String
    ): UserResponseType

    /**
     * Un-assign a Role from a User in the RBAC permission system.
     */
    @HttpRpcPOST(path = "removeRole", description = "Un-assign a role from a user")
    fun removeRole(
        @HttpRpcRequestBodyParameter(description = "User login name to be changed")
        loginName: String,
        @HttpRpcRequestBodyParameter(description = "Id of the role to un-assign from this user")
        roleId: String
    ): UserResponseType

    /**
     * Get a summary of a user's permissions.
     */
    @HttpRpcGET(path = "getPermissionSummary", description = "Get a summary of a User's permissions")
    fun getPermissionSummary(
        @HttpRpcQueryParameter(description = "Login Name of the user.")
        loginName: String
    ): UserPermissionSummaryResponseType

}