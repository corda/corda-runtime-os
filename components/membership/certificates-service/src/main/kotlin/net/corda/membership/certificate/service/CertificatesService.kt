package net.corda.membership.certificate.service

import net.corda.lifecycle.Lifecycle
import net.corda.membership.certificates.CertificateUsage

interface CertificatesService : Lifecycle {

    /**
     * Import certificate chain.
     *
     * @param typeOrHoldingId can either be a holding identity ID, or a certificate type  for a cluster-level certificate.
     * @param alias Unique alias of the certificate.
     * @param certificates The certificates in PEM format
     * @throws Exception in case of network or persistent error.
     */
    fun importCertificates(typeOrHoldingId: CertificateUsage, alias: String, certificates: String)

    /**
     * Retrieve certificate.
     *
     * @param typeOrHoldingId can either be a holding identity ID, or a certificate type  for a cluster-level certificate.
     * @param alias Unique alias of the certificate
     * @return Certificate in PEM format.
     */
    fun retrieveCertificates(typeOrHoldingId: CertificateUsage, alias: String): String?

    /**
     * Retrieve all certificates for given tenant.
     *
     * @param typeOrHoldingId can either be a holding identity ID, or a certificate type  for a cluster-level certificate.
     * @return Certificates in PEM format.
     */
    fun retrieveAllCertificates(typeOrHoldingId: CertificateUsage): List<String>
}
