package net.corda.p2p.crypto

import net.corda.p2p.crypto.protocol.api.CertificateValidator
import net.corda.p2p.crypto.protocol.api.InvalidPeerCertificate
import net.corda.p2p.crypto.protocol.api.RevocationCheckMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.security.auth.x500.X500Principal

class CertificateValidatorTest {

    private val certificateFactory = CertificateFactory.getInstance("X.509")
    private val aliceX500Principle =  X500Principal("CN=alice, OU=MyUnit, O=MyOrg, L=London, S=London, C=GB")
    private val revokedX500Principle =  X500Principal("CN=revoked, OU=MyUnit, O=MyOrg, L=London, S=London, C=GB")
    private val aliceCert = javaClass.classLoader.getResource("alice.pem")?.readText()
    private val revokedCert = javaClass.classLoader.getResource("revoked.pem")?.readText()

    private val trustStore =  javaClass.classLoader.getResource("truststore/certificate.pem")?.readText()?.let { pemCertificate ->
        KeyStore.getInstance("PKCS12").also { keyStore ->
            keyStore.load(null, null)
            val certificate = ByteArrayInputStream(pemCertificate.toByteArray()).use {
                certificateFactory.generateCertificate(it)
            }
            keyStore.setCertificateEntry("session-0", certificate)
        }
    }

    private val wrongTrustStore =  javaClass.classLoader.getResource("truststore/wrongCertificate.pem")?.readText()?.let { pemCertificate ->
        KeyStore.getInstance("PKCS12").also { keyStore ->
            keyStore.load(null, null)
            val certificate = ByteArrayInputStream(pemCertificate.toByteArray()).use {
                certificateFactory.generateCertificate(it)
            }
            keyStore.setCertificateEntry("session-0", certificate)
        }
    }

    @Test
    fun `valid certificate passes validation`() {
        val validator = CertificateValidator(RevocationCheckMode.HARD_FAIL, trustStore!!)
        validator.validate(listOf(aliceCert!!), aliceX500Principle)
    }

    @Test
    fun `revoked certificate fails validation with HARD FAIL mode`() {
        val validator = CertificateValidator(RevocationCheckMode.HARD_FAIL, trustStore!!)
        assertThrows<InvalidPeerCertificate> { validator.validate(listOf(revokedCert!!), revokedX500Principle) }
    }

    @Test
    fun `revoked certificate fails validation with SOFT FAIL mode`() {
        val validator = CertificateValidator(RevocationCheckMode.SOFT_FAIL, trustStore!!)
        assertThrows<InvalidPeerCertificate> { validator.validate(listOf(revokedCert!!), revokedX500Principle) }
    }

    @Test
    fun `revoked certificate passes validation with revocation OFF`() {
        val validator = CertificateValidator(RevocationCheckMode.OFF, trustStore!!)
        validator.validate(listOf(revokedCert!!), revokedX500Principle)
    }

    @Test
    fun `if truststore is wrong validation fails`() {
        val validator = CertificateValidator(RevocationCheckMode.HARD_FAIL, wrongTrustStore!!)
        assertThrows<InvalidPeerCertificate> { validator.validate(listOf(aliceCert!!), aliceX500Principle) }
    }
}