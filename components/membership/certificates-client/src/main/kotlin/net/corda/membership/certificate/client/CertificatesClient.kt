package net.corda.membership.certificate.client

import net.corda.crypto.core.ShortHash
import net.corda.lifecycle.Lifecycle

/**
 * A client that handles certificates requests.
 */
interface CertificatesClient : Lifecycle, DbCertificateClient {

    /**
     * Set up locally hosted identity.
     *
     *
     * @param holdingIdentityShortHash ID of the holding identity to be published.
     * @param p2pTlsCertificateChainAlias The certificates chain alias.
     * @param useClusterLevelTlsCertificateAndKey Should we use the P2P cluster level TLS certificate type and P2P key or
     *   the virtual node certificate and key.
     * @param sessionKeyId The session key ID (will use the first one if null).
     * @param sessionCertificateChainAlias The certificate chain alias of the Session Key. Should be null if no PKI is used for sessions.
     * @throws CertificatesResourceNotFoundException if a resource was not found.
     */
    @Suppress("LongParameterList")
    fun setupLocallyHostedIdentity(
        holdingIdentityShortHash: ShortHash,
        p2pTlsCertificateChainAlias: String,
        useClusterLevelTlsCertificateAndKey: Boolean,
        sessionKeyId: String?,
        sessionCertificateChainAlias: String?,
    )
}
