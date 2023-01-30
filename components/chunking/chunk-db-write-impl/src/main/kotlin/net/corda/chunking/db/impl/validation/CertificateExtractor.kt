package net.corda.chunking.db.impl.validation

import net.corda.data.certificates.CertificateUsage
import net.corda.membership.certificate.service.CertificatesService
import org.slf4j.LoggerFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

internal class CertificateExtractor(
    private val certificatesService: CertificatesService,
    private val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509")
) {
    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    fun getAllCertificates(): Collection<X509Certificate> {
        return certificatesService
            .client
            .getCertificateAliases(CertificateUsage.CODE_SIGNER, null).mapNotNull {
                certificatesService
                    .client
                    .retrieveCertificates(null, CertificateUsage.CODE_SIGNER, it)
            }.also {
                if (it.isEmpty()) {
                    logger.warn("No trusted certificates for package validation found")
                }
            }.flatMap {
                certificateFactory.generateCertificates(
                    it.byteInputStream()
                ).filterIsInstance<X509Certificate>()
            }
    }
}
