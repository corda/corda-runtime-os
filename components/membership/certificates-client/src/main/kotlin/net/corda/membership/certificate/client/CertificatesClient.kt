package net.corda.membership.certificate.client

import net.corda.crypto.core.ShortHash
import net.corda.lifecycle.Lifecycle

/**
 * A client that handles certificates requests.
 */
interface CertificatesClient : Lifecycle, DbCertificateClient {

    /**
     * A session key and certificate.
     *
     * @param sessionKeyId The session key ID.
     * @param sessionCertificateChainAlias The certificate chain alias of the Session Key.
     *   Should be null if no PKI is used for sessions.
     */
    data class SessionKeyAndCertificate(
        val sessionKeyId: ShortHash,
        val sessionCertificateChainAlias: String?,
    )

    /**
     * Set up locally hosted identity.
     *
     *
     * @param holdingIdentityShortHash ID of the holding identity to be published.
     * @param p2pTlsCertificateChainAlias The certificates chain alias.
     * @param useClusterLevelTlsCertificateAndKey Should we use the P2P cluster level TLS certificate type and P2P key or
     *   the virtual node certificate and key.
     * @param preferredSessionKeyAndCertificate The preferred session keys.
     * @param alternativeSessionKeyAndCertificates Alternative session keys.
     * @throws CertificatesResourceNotFoundException if a resource was not found.
     */
    @Suppress("LongParameterList")
    fun setupLocallyHostedIdentity(
        holdingIdentityShortHash: ShortHash,
        p2pTlsCertificateChainAlias: String,
        useClusterLevelTlsCertificateAndKey: Boolean,
        preferredSessionKeyAndCertificate: SessionKeyAndCertificate,
        alternativeSessionKeyAndCertificates: List<SessionKeyAndCertificate>,
    )
}
