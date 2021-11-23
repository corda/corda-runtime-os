package net.corda.libs.permissions.endpoints.v1.user

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
import net.corda.lifecycle.Lifecycle

@HttpRpcResource(
    name = "UserEndpoint",
    description = "User Management APIs",
    path = "/v1/user"
)
interface UserEndpoint : PluggableRPCOps<UserEndpoint>, Lifecycle {

    @HttpRpcPOST(description = "Create a User", path = "createUser")
    fun createUser(
        @HttpRpcRequestBodyParameter(description = "Details of the user to be created", required = true)
        createUserType: CreateUserType
    ): UserResponseType

    @HttpRpcGET(description = "Get a User by Login Name", path = "")
    fun getUser(
        @HttpRpcQueryParameter(name = "loginName", description = "Login Name of the user to be returned.", required = true)
        loginName: String
    ): UserResponseType?
}