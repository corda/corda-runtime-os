package net.corda.membership.httprpc.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.membership.httprpc.v1.types.response.RegistrationRequestProgress

/**
 * MGM RPC operations within a group.
 */
@HttpRpcResource(
    name = "MGMRpcOps",
    description = "Membership Group Management APIs",
    path = "mgm"
)
interface MGMRpcOps : RpcOps {
    /**
     * GET endpoint to fetch the requested group policy string
     *
     * @param holdingIdentityId The ID of the holding identity to be checked.
     * @return [Set<Map.Entry<String, String?>>] to indicate the last known status of the registration request based on
     *  local member data.
     */
    @HttpRpcGET(
        path = "{holdingIdentityId}/info",
        description = "Fetches the requested group policy string"
    )
    fun generateGroupPolicy(
        @HttpRpcPathParameter(description = "ID of the holding identity to be checked.")
        holdingIdentityId: String
    ): Set<Map.Entry<String, String?>>
}