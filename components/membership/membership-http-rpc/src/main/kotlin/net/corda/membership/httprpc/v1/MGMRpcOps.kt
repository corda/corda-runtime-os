package net.corda.membership.httprpc.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcResource

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
     * @return [String] MGM Generated Group Policy JSON.
     *  local member data.
     */
    @HttpRpcGET(
        path = "{holdingIdentityId}/info",
        description = "Fetches the requested group policy string"
    )
    fun generateGroupPolicy(
        @HttpRpcPathParameter(description = "ID of the holding identity to be checked.")
        holdingIdentityId: String
    ): String
}