package net.corda.membership.certificate.client

import net.corda.lifecycle.Lifecycle

/**
 * A client that handle certificates requests
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
     * Setup locally hosted identity.
     *
     *
     * @param holdingIdentityShortHash ID of the holding identity to be published.
     * @param certificateChainAlias The certificates chain alias.
     * @param tlsTenantId The TLS tenant ID (either p2p ot the holdingIdentityShortHash, default to the holdingIdentityShortHash).
     * @param sessionKeyId The session key ID (will use the first one if null).
     * @throws CertificatesResourceNotFoundException if a resource was not found
     */
    fun setupLocallyHostedIdentity(
        holdingIdentityShortHash: String,
        certificateChainAlias: String,
        tlsTenantId: String?,
        sessionKeyId: String?
    )
}
