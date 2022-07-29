package net.corda.membership.httprpc.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPUT
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.membership.httprpc.v1.types.request.HostedIdentitySetupRequest

@HttpRpcResource(
    name = "Network API",
    description = "Network Management endpoints.",
    path = "network"
)
interface NetworkRpcOps : RpcOps {

    /**
     * PUT endpoint which set up the identity network.
     *
     * @param holdingIdentityShortHash ID of the holding identity to set up.

     */
    @HttpRpcPUT(
        path = "setup/{holdingIdentityShortHash}",
        description = "Set up the holding identity on the network."
    )
    fun setupHostedIdentities(
        @HttpRpcPathParameter(description = "ID of the holding identity to set up.")
        holdingIdentityShortHash: String,
        @HttpRpcRequestBodyParameter(
            description = "Additional parameters for setting up the hosted identity.",
        )
        request: HostedIdentitySetupRequest
    )
}
