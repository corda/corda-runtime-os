package net.corda.membership.certificate.service

import net.corda.lifecycle.Lifecycle

interface CertificatesService : Lifecycle {

    /**
     * Import certificate chain.
     *
     * @param tenantId can either be a holding identity ID, the value 'p2p' for a cluster-level tenant
     *          of the P2P services, or 'rpc-api' for a cluster-level tenant of the HTTP RPC API
     * @param alias Unique alias of the certificate.
     * @param certificates The certificates in PEM format
     * @throws Exception in case of network or persistent error.
     */
    fun importCertificates(tenantId: String, alias: String, certificates: String)

    /**
     * Retrieve certificate.
     *
     * @param tenantId can either be a holding identity ID, the value 'p2p' for a cluster-level tenant
     *          of the P2P services, or 'rpc-api' for a cluster-level tenant of the HTTP RPC API
     * @param alias Unique alias of the certificate
     * @return Certificate in PEM format.
     */
    fun retrieveCertificates(tenantId: String, alias: String): String?

    /**
     * Retrieve all certificates for given tenant.
     *
     * @param tenantId can either be a holding identity ID, the value 'p2p' for a cluster-level tenant
     *      of the P2P services, or 'rpc-api' for a cluster-level tenant of the HTTP RPC API
     * @return Certificates in PEM format.
     */
    fun retrieveAllCertificates(tenantId: String): List<String>
}
