package net.corda.libs.permissions.endpoints.v1.user

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
import net.corda.lifecycle.Lifecycle

@HttpRpcResource(
    name = "UserEndpoint",
    description = "User Management APIs",
    path = "/:virtualNodeId/v1/user"
)
interface UserEndpoint : RpcOps, Lifecycle {

    // This URL is `https://<host>:<port>/<virtualNodeId>/v1/user/createUser`
    @HttpRpcPOST(description = "Create a User", path = "createUser")
    fun createUser(
        @HttpRpcPathParameter(name = "virtualNodeId", description = "Id of the virtual node in which the user belongs.")
        virtualNodeId: String,
        @HttpRpcRequestBodyParameter(description = "Details of the user to be created", required = true)
        createUserType: CreateUserType
    ): UserResponseType

    // This URL is `https://<host>:<port>/<virtualNodeId>/v1/user?loginName=<loginName>`
    @HttpRpcGET(description = "Get a User by Login Name", path = "")
    fun getUser(
        @HttpRpcPathParameter(name = "virtualNodeId", description = "Id of the virtual node in which the user can be found.")
        virtualNodeId: String,
        @HttpRpcQueryParameter(name = "loginName", description = "Login Name of the user to be returned.", required = true)
        loginName: String
    ): UserResponseType?
}