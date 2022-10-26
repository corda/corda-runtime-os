package net.corda.p2p.crypto.protocol.api

import net.corda.crypto.utils.convertToKeyStore
import net.corda.testing.p2p.certificates.Certificates
import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.cert.CertificateFactory

class CertificateValidatorTest {
    private companion object {
        const val keyStoreAlias = "session"
    }
    private val certificateFactory = CertificateFactory.getInstance("X.509")
    private val aliceX500Name =  MemberX500Name.parse("CN=Alice, O=R3 Test, L=London, S=London, C=GB")
    private val revokedBobX500Name =  MemberX500Name.parse("CN=Bob, O=R3 Test, L=London, S=London, C=GB")
    private val aliceCert = Certificates.aliceKeyStorePem.readText()
    private val revokedBobCert = Certificates.bobKeyStorePem.readText()
    private val trustStore = convertToKeyStore(
        certificateFactory, listOf(Certificates.truststoreCertificatePem.readText()), keyStoreAlias
    )
    private val wrongTrustStore = convertToKeyStore(
        certificateFactory, listOf(Certificates.c4TruststoreCertificatePem.readText()), keyStoreAlias
    )

    @Test
    fun `valid certificate passes validation`() {
        val validator = CertificateValidator(RevocationCheckMode.HARD_FAIL, trustStore!!)
        validator.validate(listOf(aliceCert), aliceX500Name)
    }

    @Test
    fun `revoked certificate fails validation with HARD FAIL mode`() {
        val validator = CertificateValidator(RevocationCheckMode.HARD_FAIL, trustStore!!)
        assertThrows<InvalidPeerCertificate> { validator.validate(listOf(revokedBobCert), revokedBobX500Name) }
    }

    @Test
    fun `revoked certificate fails validation with SOFT FAIL mode`() {
        val validator = CertificateValidator(RevocationCheckMode.SOFT_FAIL, trustStore!!)
        assertThrows<InvalidPeerCertificate> { validator.validate(listOf(revokedBobCert), revokedBobX500Name) }
    }

    @Test
    fun `revoked certificate passes validation with revocation OFF`() {
        val validator = CertificateValidator(RevocationCheckMode.OFF, trustStore!!)
        validator.validate(listOf(revokedBobCert), revokedBobX500Name)
    }

    @Test
    fun `if truststore is wrong validation fails`() {
        val validator = CertificateValidator(RevocationCheckMode.HARD_FAIL, wrongTrustStore!!)
        assertThrows<InvalidPeerCertificate> { validator.validate(listOf(aliceCert), aliceX500Name) }
    }
}