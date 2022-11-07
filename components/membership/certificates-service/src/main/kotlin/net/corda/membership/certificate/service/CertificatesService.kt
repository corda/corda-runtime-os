package net.corda.membership.certificate.service

import net.corda.data.certificates.CertificateUsage
import net.corda.lifecycle.Lifecycle
import net.corda.virtualnode.ShortHash

interface CertificatesService : Lifecycle {

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
        certificates: String,
    )

    /**
     * Retrieve certificate.
     *
     * @param usage The certificate usage
     * @param holdingIdentityId The holding Identity ID. null for a cluster-level certificate.
     * @param alias Unique alias of the certificate
     * @return Certificate in PEM format.
     */
    fun retrieveCertificates(
        usage: CertificateUsage,
        holdingIdentityId: ShortHash?,
        alias: String,
    ): String?

    /**
     * Retrieve all certificates for given tenant.
     *
     * @param usage The certificate usage
     * @param holdingIdentityId The holding Identity ID. null for a cluster-level certificate.
     * @return Certificates in PEM format.
     */
    fun retrieveAllCertificates(usage: CertificateUsage, holdingIdentityId: ShortHash?): List<String>
}
