package net.corda.membership.certificate.client

import net.corda.lifecycle.Lifecycle

/**
 * A client that handle certificates requests
 */
interface CertificatesClient : Lifecycle {

    /**
     * Import a certificate.
     *
     * @param tenantId 'p2p', 'rpc-api', or holding identity identity ID.
     * @param alias Unique alias of the certificate.
     * @param certificate The certificate in PEM format
     * @throws Exception in case of network or persistent error.
     */
    fun importCertificate(tenantId: String, alias: String, certificate: String)
}
