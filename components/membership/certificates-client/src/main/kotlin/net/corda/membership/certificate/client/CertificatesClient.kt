package net.corda.membership.certificate.client

import net.corda.lifecycle.Lifecycle
import net.corda.membership.certificates.CertificateUsage
import net.corda.virtualnode.ShortHash

/**
 * A client that handles certificates requests.
 */
interface CertificatesClient : Lifecycle {

    /**
     * Import certificate chain.
     *
     * @param usage Certificate usage (type of holding identity).
     * @param alias Unique alias of the certificate.
     * @param certificates The certificates in PEM format
     * @throws Exception in case of network or persistent error.
     */
    fun importCertificates(usage: CertificateUsage, alias: String, certificates: String)

    /**
     * Set up locally hosted identity.
     *
     *
     * @param holdingIdentityShortHash ID of the holding identity to be published.
     * @param p2pTlsCertificateChainAlias The certificates chain alias.
     * @param useClusterLevelCertificateAndKey Should we use the P2P cluster level certificate type and P2P key or
     *   the virtual node cluster and key.
     * @param sessionKeyTenantId The tenant ID under which the session initiation key is stored (defaults to [holdingIdentityShortHash]).
     * @param sessionKeyId The session key ID (will use the first one if null).
     * @throws CertificatesResourceNotFoundException if a resource was not found.
     */
    fun setupLocallyHostedIdentity(
        holdingIdentityShortHash: ShortHash,
        p2pTlsCertificateChainAlias: String,
        useClusterLevelCertificateAndKey: Boolean,
        sessionKeyTenantId: String?,
        sessionKeyId: String?
    )
}
