package net.corda.membership.certificate.client

import net.corda.lifecycle.Lifecycle
import net.corda.virtualnode.ShortHash

/**
 * A client that handles certificates requests.
 */
interface CertificatesClient : Lifecycle {

    /**
     * Import certificate chain.
     *
     * @param tenantId can either be a holding identity ID, the value 'p2p' for a cluster-level tenant
     *      of the P2P services, or 'rpc-api' for a cluster-level tenant of the HTTP RPC API
     * @param alias Unique alias of the certificate.
     * @param certificates The certificates in PEM format
     * @throws Exception in case of network or persistent error.
     */
    fun importCertificates(tenantId: String, alias: String, certificates: String)

    /**
     * Set up locally hosted identity.
     *
     *
     * @param holdingIdentityShortHash ID of the holding identity to be published.
     * @param p2pTlsCertificateChainAlias The certificates chain alias.
     * @param p2pTlsTenantId The TLS tenant ID (either p2p or the holdingIdentityShortHash, defaults to [holdingIdentityShortHash]).
     * @param sessionKeyTenantId The tenant ID under which the session initiation key is stored (defaults to [holdingIdentityShortHash]).
     * @param sessionKeyId The session key ID (will use the first one if null).
     * @param sessionCertificateChainAlias The certificate chain alias of the Session Key. Should be null if no PKI is used for sessions.
     * @throws CertificatesResourceNotFoundException if a resource was not found.
     */
    fun setupLocallyHostedIdentity(
        holdingIdentityShortHash: ShortHash,
        p2pTlsCertificateChainAlias: String,
        p2pTlsTenantId: String?,
        sessionKeyTenantId: String?,
        sessionKeyId: String?,
        sessionCertificateChainAlias: String?
    )
}
