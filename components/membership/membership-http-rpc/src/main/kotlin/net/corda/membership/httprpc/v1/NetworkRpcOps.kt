package net.corda.membership.httprpc.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPUT
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.membership.httprpc.v1.types.request.HostedIdentitySetupRequest

/**
 * The Network API consists of endpoints which manage the setup of holding identities in P2P networks. The API allows
 * you to set up a holding identity on the network and configure properties required for P2P messaging.
 */
@HttpRpcResource(
    name = "Network API",
    description = "The Network API consists of endpoints which manage the setup of holding identities in P2P networks.",
    path = "network"
)
interface NetworkRpcOps : RpcOps {

    /**
     * The [setupHostedIdentities] method enables you to configure the holding identity represented by
     * [holdingIdentityShortHash] as a network participant by setting properties required for P2P messaging to work. This
     * method does not return anything if the configuration is completed successfully.
     *
     * Example usage:
     * ```
     * networkOps.setupHostedIdentities(holdingIdentityShortHash = "58B6030FABDD", request =
     * HostedIdentitySetupRequest("TLS_CERT_ALIAS", "58B6030FABDD", "58B6030FABDD", "3B9A266F96E2"))
     * ```
     *
     * @param holdingIdentityShortHash ID of the holding identity to set up.
     * @param request [HostedIdentitySetupRequest] which contains properties for P2P messaging including the P2P TLS
     * certificate chain alias, the TLS tenant ID (either 'p2p' or [holdingIdentityShortHash]), the tenant ID under
     * which the session initiation key is stored, and the session key identifier.
     */
    @HttpRpcPUT(
        path = "setup/{holdingIdentityShortHash}",
        description = "This method configures a holding identity as a network participant by setting properties " +
                "required for P2P messaging."
    )
    fun setupHostedIdentities(
        @HttpRpcPathParameter(description = "ID of the holding identity to set up")
        holdingIdentityShortHash: String,
        @HttpRpcRequestBodyParameter(
            description = """
                Request object which contains properties for P2P messaging including:
                p2pTlsCertificateChainAlias: the P2P TLS certificate chain alias
                p2pTlsTenantId: the TLS tenant ID (either 'p2p' or holding identity ID)
                sessionKeyTenantId: the tenant ID under which the session initiation key is stored
                sessionKeyId: the session key identifier""",
        )
        request: HostedIdentitySetupRequest
    )
}
