package net.corda.membership.httprpc

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.membership.httprpc.types.MemberInfoSubmitted
import net.corda.membership.httprpc.types.RegistrationRequestProgress

@HttpRpcResource(
    name = "RegistrationRpcOps",
    description = "Membership Registration APIs",
    path = "membership"
)
interface RegistrationRpcOps : RpcOps {
    @HttpRpcPOST(description = "Start registration process for a virtual node.", path = "register")
    fun startRegistration(
        @HttpRpcRequestBodyParameter(description = "ID of the virtual node to be registered.", required = true)
        virtualNodeId: String
    ): MemberInfoSubmitted

    @HttpRpcGET(description = "Checks the status of the registration request.", path = "")
    fun checkRegistrationProgress(
        @HttpRpcQueryParameter(name = "virtualNodeId", description = "ID of the virtual node to be checked.", required = true)
        virtualNodeId: String
    ): RegistrationRequestProgress
}