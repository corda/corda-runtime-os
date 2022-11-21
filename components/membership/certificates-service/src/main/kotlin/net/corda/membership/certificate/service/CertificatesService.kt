package net.corda.membership.certificate.service

import net.corda.lifecycle.Lifecycle
import net.corda.membership.certificate.client.DbCertificateClient

interface CertificatesService : Lifecycle {
    val client: DbCertificateClient
}
