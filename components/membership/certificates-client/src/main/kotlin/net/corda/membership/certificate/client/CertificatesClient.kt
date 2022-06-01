package net.corda.membership.certificate.client

import net.corda.lifecycle.Lifecycle

interface CertificatesClient : Lifecycle {
    fun importCertificate(tenantId: String, alias: String, certificate: String)
}
