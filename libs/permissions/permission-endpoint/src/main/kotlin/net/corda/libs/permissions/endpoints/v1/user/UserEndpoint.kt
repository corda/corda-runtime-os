package net.corda.libs.permissions.endpoints.v1.user

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.libs.permission.PermissionValidator
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.lifecycle.Lifecycle

/**
 * User endpoint exposes HTTP endpoints for management of Users in the RBAC permission system.
 */
@HttpRpcResource(
    name = "UserEndpoint",
    description = "User Management APIs",
    path = "/v1/user"
)
interface UserEndpoint : PluggableRPCOps<UserEndpoint>, Lifecycle {
    /**
     * Permission manager for executing operations for permission management of the RBAC permission system.
     *
     * When null, permission management operations cannot be serviced. The endpoint should respond appropriately.
     */
    var permissionManager: PermissionManager?

    /**
     * Permission validator for extra validation on a per endpoint basis.
     *
     * When null, additional permission validation cannot be performed. The endpoint should respond appropriately.
     */
    var permissionValidator: PermissionValidator?

    /**
     * Create a user in the RBAC permission system.
     */
    @HttpRpcPOST(description = "Create a User", path = "createUser")
    fun createUser(
        @HttpRpcRequestBodyParameter(description = "Details of the user to be created", required = true)
        createUserType: CreateUserType
    ): UserResponseType

    /**
     * Get a user by loginName in the RBAC permission system.
     */
    @HttpRpcGET(description = "Get a User by Login Name", path = "")
    fun getUser(
        @HttpRpcQueryParameter(name = "loginName", description = "Login Name of the user to be returned.", required = true)
        loginName: String
    ): UserResponseType?

}