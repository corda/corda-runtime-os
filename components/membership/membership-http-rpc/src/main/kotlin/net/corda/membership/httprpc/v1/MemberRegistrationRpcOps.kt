package net.corda.membership.httprpc.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.membership.httprpc.v1.types.request.MemberRegistrationRequest
import net.corda.membership.httprpc.v1.types.response.RegistrationRequestProgress

/**
 * RPC operations for registering a member (i.e. virtual node) within a group.
 */
@HttpRpcResource(
    name = "MemberRegistrationRpcOps",
    description = "Membership Registration APIs",
    path = "membership"
)
interface MemberRegistrationRpcOps : RpcOps {
    /**
     * POST endpoint which starts the registration process for a member.
     *
     * @param memberRegistrationRequest Data necessary to include in order to initiate registration.
     *
     * @return [RegistrationRequestProgress] to indicate the status of the request at time of submission.
     */
    @HttpRpcPOST(
        description = "Start registration process for a virtual node.",
        path = "register"
    )
    fun startRegistration(
        @HttpRpcRequestBodyParameter(
            description = "Data required to initialise the registration process.",
            required = true
        )
        memberRegistrationRequest: MemberRegistrationRequest
    ): RegistrationRequestProgress

    /**
     * GET endpoint which checks the latest known status of registration based on a member's own local data and without
     * outwards communication.
     *
     * @param virtualNodeId The ID of the virtual node to be checked.
     * @return [RegistrationRequestProgress] to indicate the last known status of the registration request based on
     *  local member data.
     */
    @HttpRpcGET(
        description = "Checks the status of the registration request.",
        path = ""
    )
    fun checkRegistrationProgress(
        @HttpRpcQueryParameter(
            name = "virtualNodeId",
            description = "ID of the virtual node to be checked.",
            required = true
        )
        virtualNodeId: String
    ): RegistrationRequestProgress
}