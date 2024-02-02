package net.corda.p2p.crypto.protocol.api

import net.corda.data.p2p.crypto.protocol.RevocationCheckMode
import net.corda.data.p2p.gateway.certificates.Active
import net.corda.data.p2p.gateway.certificates.RevocationCheckResponse
import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.security.cert.CertPath
import java.security.cert.CertPathValidator
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.PKIXBuilderParameters
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

class CertificateValidatorTest {

    private val aliceX500Name = MemberX500Name.parse("CN=alice, OU=MyUnit, O=MyOrg, L=London, S=London, C=GB")
    private val certX500Name = MemberX500Name.parse("CN=cert, OU=MyUnit, O=MyOrg, L=London, S=London, C=GB")
    private val certPathValidator = mock<CertPathValidator>()
    private val certificatePemString = "certificate"
    private val publicKey = mock<PublicKey>()
    private val certificate = mock<X509Certificate> {
        on { subjectX500Principal } doReturn certX500Name.x500Principal
        on { publicKey } doReturn publicKey
    }
    private val certificateChain = mock<CertPath> {
        on { certificates } doReturn listOf(certificate)
    }
    private val certificateFactory = mock<CertificateFactory> {
        on { generateCertPath(any<MutableList<Certificate>>()) } doReturn certificateChain
        on { generateCertificate(any()) } doReturn certificate
    }
    private val mockInvalidPeerCertificate = Mockito.mockConstruction(InvalidPeerCertificate::class.java)
    private val mockPKIXBuilderParameters = Mockito.mockConstruction(PKIXBuilderParameters::class.java)

    @AfterEach
    fun cleanUp() {
        mockInvalidPeerCertificate.close()
        mockPKIXBuilderParameters.close()
    }

    @Test
    fun `certificate fails validation certificate cannot be read`() {
        whenever(certificate.keyUsage).thenReturn(BooleanArray(10) { it == 0 }) // Set key usage bit
        whenever(certificateFactory.generateCertificate(any())).thenThrow(CertificateException("Invalid certificate."))
        val validator = CertificateValidator(
            RevocationCheckMode.HARD_FAIL,
            mock(),
            { RevocationCheckResponse(Active()) },
            certPathValidator,
            certificateFactory,
        )
        assertThrows<InvalidPeerCertificate> { validator.validate(listOf(certificatePemString), certX500Name, publicKey) }
    }

    @Test
    fun `certificate fails validation if X500 name doesn't match`() {
        val validator = CertificateValidator(
            RevocationCheckMode.HARD_FAIL,
            mock(),
            { RevocationCheckResponse(Active()) },
            certPathValidator,
            certificateFactory,
        )
        assertThrows<InvalidPeerCertificate> { validator.validate(listOf(certificatePemString), aliceX500Name, publicKey) }
    }

    @Test
    fun `certificate fails validation if cert if not an X509Certificate`() {
        val nonX500Certificate = mock<Certificate>()
        whenever(certificateChain.certificates).thenReturn(listOf(nonX500Certificate))
        val validator = CertificateValidator(
            RevocationCheckMode.HARD_FAIL,
            mock(),
            { RevocationCheckResponse(Active()) },
            certPathValidator,
            certificateFactory,
        )
        assertThrows<InvalidPeerCertificate> { validator.validate(listOf(certificatePemString), certX500Name, publicKey) }
    }

    @Test
    fun `certificate fails validation if x500 name is not a valid corda x500 name`() {
        val x500NoLocality = X500Principal("CN=alice, OU=MyUnit, O=MyOrg, C=GB") // Valid X500Name but not a valid Corda X500Name
        val certificate = mock<X509Certificate> {
            on { subjectX500Principal } doReturn x500NoLocality
        }
        whenever(certificateChain.certificates).thenReturn(listOf(certificate))
        val validator = CertificateValidator(
            RevocationCheckMode.HARD_FAIL,
            mock(),
            { RevocationCheckResponse(Active()) },
            certPathValidator,
            certificateFactory,
        )
        assertThrows<InvalidPeerCertificate> { validator.validate(listOf(certificatePemString), certX500Name, publicKey) }
    }

    @Test
    fun `certificate fails validation if x509 cert does not have digital signature set`() {
        whenever(certificate.keyUsage).thenReturn(BooleanArray(10) { it != 0 })
        val validator = CertificateValidator(
            RevocationCheckMode.HARD_FAIL,
            mock(),
            { RevocationCheckResponse(Active()) },
            certPathValidator,
            certificateFactory,
        )
        assertThrows<InvalidPeerCertificate> { validator.validate(listOf(certificatePemString), certX500Name, publicKey) }
    }

    @Test
    fun `certificate fails validation if trust store can not be read`() {
        val pemTruststore = listOf("Not a certificate.")
        whenever(certificate.keyUsage).thenReturn(BooleanArray(10) { it == 0 }) // Set key usage bit
        whenever(certificateFactory.generateCertificate(any()))
            .thenReturn(certificate).thenThrow(CertificateException("Invalid certificate."))
        val validator = CertificateValidator(
            RevocationCheckMode.HARD_FAIL,
            pemTruststore,
            { RevocationCheckResponse(Active()) },
            certPathValidator,
            certificateFactory,
        )
        assertThrows<InvalidPeerCertificate> { validator.validate(listOf(certificatePemString), certX500Name, publicKey) }
    }
}
