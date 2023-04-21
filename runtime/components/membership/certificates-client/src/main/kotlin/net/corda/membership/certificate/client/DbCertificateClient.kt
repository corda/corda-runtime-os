package net.corda.membership.certificate.client

import net.corda.crypto.core.ShortHash
import net.corda.data.certificates.CertificateUsage

/**
 * A client that handles persistence related certificates requests.
 */
interface DbCertificateClient {
    /**
     * Import certificate chain.
     *
     * @param usage The certificate usage
     * @param holdingIdentityId The holding Identity ID. null for a cluster-level certificate.
     * @param alias Unique alias of the certificate.
     * @param certificates The certificates in PEM format
     * @throws Exception in case of network or persistent error.
     */
    fun importCertificates(
        usage: CertificateUsage,
        holdingIdentityId: ShortHash?,
        alias: String,
        certificates: String
    )

    /**
     * Get all certificate aliases.
     *
     * @param usage The certificate usage
     * @param holdingIdentityId The holding Identity ID. null for a cluster-level certificate.
     * @throws Exception in case of network or persistent error.
     */
    fun getCertificateAliases(
        usage: CertificateUsage,
        holdingIdentityId: ShortHash?,
    ): Collection<String>

    /**
     * Get certificate content.
     *
     * @param holdingIdentityId The holding Identity ID. null for a cluster-level certificate.
     * @param usage The certificate usage
     * @param alias Unique alias of the certificate.
     * @throws Exception in case of network or persistent error.
     */
    fun retrieveCertificates(
        holdingIdentityId: ShortHash?,
        usage: CertificateUsage,
        alias: String,
    ): String?
}
