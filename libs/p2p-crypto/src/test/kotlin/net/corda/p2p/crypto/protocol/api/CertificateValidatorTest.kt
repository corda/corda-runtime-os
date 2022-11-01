package net.corda.p2p.crypto.protocol.api

import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyStore
import java.security.cert.CertPath
import java.security.cert.CertPathValidator
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.PKIXBuilderParameters
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

class CertificateValidatorTest {

    private val aliceX500Name =  MemberX500Name.parse("CN=alice, OU=MyUnit, O=MyOrg, L=London, S=London, C=GB")
    private val certX500Name =  MemberX500Name.parse("CN=cert, OU=MyUnit, O=MyOrg, L=London, S=London, C=GB")
    private val trustStore = mock<KeyStore>()
    private val certPathValidator = mock<CertPathValidator>()
    private val certificatePemString = "certificate"
    private val certificate = mock<X509Certificate> {
        on {subjectX500Principal} doReturn certX500Name.x500Principal
    }
    private val certificateChain = mock<CertPath> {
        on { certificates } doReturn listOf(certificate)
    }
    private val certificateFactory = mock<CertificateFactory> {
        on { generateCertPath(any<MutableList<Certificate>>()) } doReturn certificateChain
        on { generateCertificate(any()) } doReturn certificate
    }
    private val mockInvalidPeerCertificate = Mockito.mockConstruction(InvalidPeerCertificate::class.java)

    @AfterEach
    fun cleanUp() {
        mockInvalidPeerCertificate.close()
    }

    @Test
    fun `certificate fails validation if X500 name doesn't match`() {
        val validator = CertificateValidator(RevocationCheckMode.HARD_FAIL, trustStore, certPathValidator, certificateFactory)
        assertThrows<InvalidPeerCertificate> { validator.validate(listOf(certificatePemString), aliceX500Name) }
    }

    @Test
    fun `certificate fails validation if cert if not an X509Certificate`() {
        val nonX500Certificate = mock<Certificate>()
        whenever(certificateChain.certificates).thenReturn(listOf(nonX500Certificate))
        val validator = CertificateValidator(RevocationCheckMode.HARD_FAIL, trustStore, certPathValidator, certificateFactory)
        assertThrows<InvalidPeerCertificate> { validator.validate(listOf(certificatePemString), certX500Name) }
    }

    @Test
    fun `certificate fails validation if x500 name is not a valid corda x500 name`() {
        val x500NoLocality = X500Principal("CN=alice, OU=MyUnit, O=MyOrg, C=GB") //Valid X500Name but not a valid Corda X500Name
        val certificate = mock<X509Certificate> {
            on {subjectX500Principal} doReturn x500NoLocality
        }
        whenever(certificateChain.certificates).thenReturn(listOf(certificate))
        val validator = CertificateValidator(RevocationCheckMode.HARD_FAIL, trustStore, certPathValidator, certificateFactory)
        assertThrows<InvalidPeerCertificate> { validator.validate(listOf(certificatePemString), certX500Name) }
    }

    @Test
    fun `certificate fails validation if x509 cert does not have digital signature set`() {
        whenever(certificate.keyUsage).thenReturn(BooleanArray(10) { it != 0 })
        whenever(trustStore.aliases()).thenReturn(any())
        val mock = Mockito.mockConstruction(PKIXBuilderParameters::class.java)
        val validator = CertificateValidator(RevocationCheckMode.HARD_FAIL, trustStore, certPathValidator, certificateFactory)
        assertThrows<InvalidPeerCertificate> { validator.validate(listOf(certificatePemString), certX500Name) }
        mock.close()
    }
}