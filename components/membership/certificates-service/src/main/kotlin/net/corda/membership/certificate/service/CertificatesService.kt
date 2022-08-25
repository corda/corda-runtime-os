package net.corda.membership.certificate.service

import net.corda.lifecycle.Lifecycle

interface CertificatesService : Lifecycle {

    /**
     * Import certificate chain.
     *
     * @param tenantId 'codesigner', 'p2p', 'rpc-api', or holding identity identity ID.
     * @param alias Unique alias of the certificate.
     * @param certificates The certificates in PEM format
     * @throws Exception in case of network or persistent error.
     */
    fun importCertificates(tenantId: String, alias: String, certificates: String)

    /**
     * Retrieve certificate.
     *
     * @param tenantId 'codesigner', 'p2p', 'rpc-api', or holding identity identity ID.
     * @param alias Unique alias of the certificate
     * @return Certificate in PEM format.
     */
    fun retrieveCertificates(tenantId: String, alias: String): String?

    /**
     * Retrieve all certificates for given tenant.
     *
     * @param tenantId 'codesigner', 'p2p', 'rpc-api', or holding identity identity ID.
     * @return Certificates in PEM format.
     */
    fun retrieveAllCertificates(tenantId: String): List<String>
}
