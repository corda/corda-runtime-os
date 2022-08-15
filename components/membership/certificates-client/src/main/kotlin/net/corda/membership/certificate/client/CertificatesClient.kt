package net.corda.membership.certificate.client

import net.corda.lifecycle.Lifecycle

/**
 * A client that handles certificates requests.
 */
interface CertificatesClient : Lifecycle {

    /**
     * Import certificate chain.
     *
     * @param tenantId 'p2p', 'rpc-api', or holding identity identity ID.
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
     * @throws CertificatesResourceNotFoundException if a resource was not found.
     */
    fun setupLocallyHostedIdentity(
        holdingIdentityShortHash: String,
        p2pTlsCertificateChainAlias: String,
        p2pTlsTenantId: String?,
        sessionKeyTenantId: String?,
        sessionKeyId: String?
    )
}
