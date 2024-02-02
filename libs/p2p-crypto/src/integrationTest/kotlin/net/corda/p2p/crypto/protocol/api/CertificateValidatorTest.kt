package net.corda.p2p.crypto.protocol.api

import net.corda.data.p2p.crypto.protocol.RevocationCheckMode
import net.corda.data.p2p.gateway.certificates.Active
import net.corda.data.p2p.gateway.certificates.RevocationCheckResponse
import net.corda.data.p2p.gateway.certificates.Revoked
import net.corda.testing.p2p.certificates.Certificates
import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory

class CertificateValidatorTest {

    private companion object {
        const val certificateFactoryType = "X.509"
        val certificateFactory: CertificateFactory = CertificateFactory.getInstance(certificateFactoryType)
    }

    private val aliceX500Name = MemberX500Name.parse("CN=Alice, O=R3 Test, L=London, C=GB")
    private val aliceCert = Certificates.aliceKeyStorePem.readText()
    private val alicePublicKey = certificateFactory.generateCertificate(
        ByteArrayInputStream(Certificates.aliceKeyStorePem.readBytes()),
    ).publicKey
    private val wrongPublicKey = certificateFactory.generateCertificate(
        ByteArrayInputStream(Certificates.bobKeyStorePem.readBytes()),
    ).publicKey
    private val trustStore = listOf(Certificates.truststoreCertificatePem.readText())
    private val trustStoreWithRevocation = listOf(Certificates.truststoreCertificatePem.readText())
    private val wrongTrustStore = listOf(Certificates.c4TruststoreCertificatePem.readText())
    private val revokedResponse = RevocationCheckResponse(Revoked("The certificate was revoked.", 0))

    @Test
    fun `valid certificate passes validation`() {
        val validator = CertificateValidator(RevocationCheckMode.HARD_FAIL, trustStore, { RevocationCheckResponse(Active()) })
        validator.validate(listOf(aliceCert), aliceX500Name, alicePublicKey)
    }

    @Test
    fun `revoked certificate fails validation with HARD FAIL mode`() {
        val validator = CertificateValidator(RevocationCheckMode.HARD_FAIL, trustStore, { revokedResponse })
        assertThrows<InvalidPeerCertificate> { validator.validate(listOf(aliceCert), aliceX500Name, alicePublicKey) }
    }

    @Test
    fun `revoked certificate fails validation with SOFT FAIL mode`() {
        val validator = CertificateValidator(RevocationCheckMode.SOFT_FAIL, trustStore, { revokedResponse })
        assertThrows<InvalidPeerCertificate> { validator.validate(listOf(aliceCert), aliceX500Name, alicePublicKey) }
    }

    @Test
    fun `revoked certificate passes validation with revocation OFF`() {
        val validator = CertificateValidator(RevocationCheckMode.OFF, trustStore, { revokedResponse })
        validator.validate(listOf(aliceCert), aliceX500Name, alicePublicKey)
    }

    @Test
    fun `if truststore is wrong validation fails`() {
        val validator = CertificateValidator(RevocationCheckMode.HARD_FAIL, wrongTrustStore, { RevocationCheckResponse(Active()) })
        assertThrows<InvalidPeerCertificate> { validator.validate(listOf(aliceCert), aliceX500Name, alicePublicKey) }
    }

    @Test
    fun `if public key does not match validation fails`() {
        val validator = CertificateValidator(RevocationCheckMode.HARD_FAIL, trustStoreWithRevocation, { RevocationCheckResponse(Active()) })
        assertThrows<InvalidPeerCertificate> { validator.validate(listOf(aliceCert), aliceX500Name, wrongPublicKey) }
    }
}
