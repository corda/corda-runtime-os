package net.corda.chunking.db.impl.validation

import net.corda.data.certificates.CertificateUsage
import net.corda.membership.certificate.client.DbCertificateClient
import net.corda.membership.certificate.service.CertificatesService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.InputStream
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class CertificateExtractorTest {
    private val certificateClient = mock<DbCertificateClient> {
        on {
            getCertificateAliases(
                CertificateUsage.CODE_SIGNER,
                null
            )
        } doReturn listOf("one", "two", "three")
        on {
            retrieveCertificates(
                null,
                CertificateUsage.CODE_SIGNER,
                "one"
            )
        } doReturn "certificateOne"
        on {
            retrieveCertificates(
                null,
                CertificateUsage.CODE_SIGNER,
                "two"
            )
        } doReturn "certificateTwo"
        on {
            retrieveCertificates(
                null,
                CertificateUsage.CODE_SIGNER,
                "three"
            )
        } doReturn null
    }
    private val service = mock<CertificatesService> {
        on { client } doReturn certificateClient
    }
    private val certificate11 = mock<X509Certificate>()
    private val certificate12 = mock<Certificate>()
    private val certificate13 = mock<X509Certificate>()
    private val certificate2 = mock<X509Certificate>()
    private val certificateFactory = mock<CertificateFactory> {
        on { generateCertificates(any()) } doAnswer {
            val input = it.arguments[0] as InputStream
            when (input.reader().readText()) {
                "certificateOne" -> listOf(certificate11, certificate12, certificate13)
                "certificateTwo" -> listOf(certificate2)
                else -> emptyList()
            }
        }
    }

    private val extractor = CertificateExtractor(
        service,
        certificateFactory,
    )

    @Test
    fun `getAllCertificates return all the certificates`() {
        val certificates = extractor.getAllCertificates()

        assertThat(certificates).containsExactlyInAnyOrder(
            certificate11,
            certificate13,
            certificate2
        )
    }

    @Test
    fun `getAllCertificates returns empty list when there are no certificates`() {
        whenever(certificateClient.getCertificateAliases(CertificateUsage.CODE_SIGNER, null)).doReturn(emptyList())

        val certificates = extractor.getAllCertificates()

        assertThat(certificates).isEmpty()
    }
}
