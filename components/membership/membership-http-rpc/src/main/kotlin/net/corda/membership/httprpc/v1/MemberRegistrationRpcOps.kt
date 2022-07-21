package net.corda.membership.httprpc.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.membership.httprpc.v1.types.request.MemberRegistrationRequest
import net.corda.membership.httprpc.v1.types.response.RegistrationRequestProgress

/**
 * RPC operations for registering a member (i.e. holding identity) within a group.
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
     * @param holdingIdentityShortHash The ID of the holding identity the member is using.
     *
     * @return [RegistrationRequestProgress] to indicate the status of the request at time of submission.
     */
    @HttpRpcPOST(
        path = "{holdingIdentityShortHash}",
        description = "Start registration process for a virtual node."
    )
    fun startRegistration(
        @HttpRpcPathParameter(description = "ID of the holding identity to be checked.")
        holdingIdentityShortHash: String,
        @HttpRpcRequestBodyParameter(
            description = "Data required to initialise the registration process."
        )
        memberRegistrationRequest: MemberRegistrationRequest
    ): RegistrationRequestProgress

    /**
     * GET endpoint which checks the latest known status of registration based on a member's own local data and without
     * outwards communication.
     *
     * @param holdingIdentityShortHash The ID of the holding identity to be checked.
     * @return [RegistrationRequestProgress] to indicate the last known status of the registration request based on
     *  local member data.
     */
    @HttpRpcGET(
        path = "{holdingIdentityShortHash}",
        description = "Checks the status of the registration request."
    )
    fun checkRegistrationProgress(
        @HttpRpcPathParameter(description = "ID of the holding identity to be checked.")
        holdingIdentityShortHash: String
    ): RegistrationRequestProgress
}
