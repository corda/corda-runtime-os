package net.corda.libs.permissions.endpoints.v1.user

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPUT
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.libs.permissions.endpoints.v1.user.types.JWTResponseType
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
    @HttpRpcPOST(description = "Create a User")
    fun createUser(
        @HttpRpcRequestBodyParameter(description = "Details of the user to be created")
        createUserType: CreateUserType
    ): UserResponseType

    /**
     * Get a user by loginName in the RBAC permission system.
     */
    @HttpRpcGET(description = "Get a User by Login Name")
    fun getUser(
        @HttpRpcQueryParameter(description = "Login Name of the user to be returned.")
        loginName: String
    ): UserResponseType

    /**
     * Assign a Role to a User in the RBAC permission system.
     */
    @HttpRpcPUT(path = "addRole", description = "Assign a Role to a User")
    fun addRole(
        @HttpRpcRequestBodyParameter(description = "User login name to be changed")
        loginName: String,
        @HttpRpcRequestBodyParameter(description = "Id of the role to associate with this user")
        roleId: String
    ): UserResponseType

    /**
     * Un-assign a Role from a User in the RBAC permission system.
     */
    @HttpRpcPUT(path = "removeRole", description = "Un-assign a role from a user")
    fun removeRole(
        @HttpRpcRequestBodyParameter(description = "User login name to be changed")
        loginName: String,
        @HttpRpcRequestBodyParameter(description = "Id of the role to un-assign from this user")
        roleId: String
    ): UserResponseType

    /**
     * Get a summary of a user's permissions.
     */
    @HttpRpcGET(path = "permissionSummary/{loginName}", description = "Get a summary of a User's permissions")
    fun getPermissionSummary(
        @HttpRpcPathParameter(description = "Login Name of the user.")
        loginName: String
    ): UserPermissionSummaryResponseType

    /**
     * Generates a JWT for the user
     */
    @HttpRpcPOST(path = "generateLocalAuthToken", description = "Generates a JWT auth token")
    fun generateLocalAuthToken(): JWTResponseType
}