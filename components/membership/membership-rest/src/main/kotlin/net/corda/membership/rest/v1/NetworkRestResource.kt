package net.corda.membership.rest.v1

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpPUT
import net.corda.httprpc.annotations.RestPathParameter
import net.corda.httprpc.annotations.RestRequestBodyParameter
import net.corda.httprpc.annotations.HttpRestResource
import net.corda.membership.rest.v1.types.request.HostedIdentitySetupRequest

/**
 * The Network API consists of endpoints which manage the setup of holding identities in P2P networks. The API allows
 * you to set up a holding identity on the network and configure properties required for P2P messaging.
 */
@HttpRestResource(
    name = "Network API",
    description = "The Network API consists of endpoints which manage the setup of holding identities in P2P networks.",
    path = "network"
)
interface NetworkRestResource : RestResource {

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
    @HttpPUT(
        path = "setup/{holdingIdentityShortHash}",
        description = "This method configures a holding identity as a network participant by setting properties " +
            "required for P2P messaging."
    )
    fun setupHostedIdentities(
        @RestPathParameter(description = "ID of the holding identity to set up")
        holdingIdentityShortHash: String,
        @RestRequestBodyParameter(
            description = """
                Request object which contains properties for P2P messaging including:
                p2pTlsCertificateChainAlias: the P2P TLS certificate chain alias
                useClusterLevelTlsCertificateAndKey: Should the cluster-level P2P TLS certificate type and key be 
                used or the virtual node certificate and key.
                useClusterLevelSessionCertificateAndKey: Should the cluster-level P2P SESSION certificate type 
                and key be used or the virtual node certificate and key.
                sessionKeyId: the session key identifier""",
        )
        request: HostedIdentitySetupRequest
    )
}
