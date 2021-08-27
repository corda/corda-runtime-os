@file:JvmName("CertUtils")

package net.corda.crypto

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.exceptions.CryptoServiceException
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.Time
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.operator.ContentSigner
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

fun createDevCertificate(
    issuer: X500Name,
    signer: ContentSigner,
    subject: X500Name,
    subjectPublicKey: PublicKey
): X509Certificate {
    val subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(ASN1Sequence.getInstance(subjectPublicKey.encoded))
    val validityWindow = getValidityWindow(Duration.ZERO, Duration.ofDays(365))
    val v3CertGen = X509v3CertificateBuilder(
        issuer,
        BigInteger.valueOf(System.currentTimeMillis()),
        Time(validityWindow.first),
        Time(validityWindow.second),
        subject,
        subjectPublicKeyInfo
    )
    return v3CertGen.build(signer).toJca()
}

fun SigningService.getSigner(schemeMetadata: CipherSchemeMetadata, alias: String): ContentSigner {
    return object : ContentSigner {
        private val publicKey: PublicKey = findPublicKey(alias)
            ?: throw CryptoServiceException("No key found for alias $alias", isRecoverable = false)
        private val signatureScheme: SignatureScheme = schemeMetadata.findSignatureScheme(publicKey)
        private val sigAlgID: AlgorithmIdentifier = signatureScheme.signatureSpec.signatureOID
            ?: throw CryptoServiceException(
                "The signature algorithm is not specified in ${signatureScheme.codeName} for alias $alias",
                isRecoverable = false
            )
        private val baos = ByteArrayOutputStream()
        override fun getAlgorithmIdentifier(): AlgorithmIdentifier = sigAlgID
        override fun getOutputStream(): OutputStream = baos
        override fun getSignature(): ByteArray = sign(alias, baos.toByteArray())
    }
}

fun FreshKeySigningService.getSigner(schemeMetadata: CipherSchemeMetadata, publicKey: PublicKey): ContentSigner {
    return object : ContentSigner {
        private val signatureScheme: SignatureScheme = schemeMetadata.findSignatureScheme(publicKey)
        private val sigAlgID: AlgorithmIdentifier = signatureScheme.signatureSpec.signatureOID
            ?: throw CryptoServiceException(
                "The signature algorithm is not specified in ${signatureScheme.codeName}",
                isRecoverable = false
            )
        private val baos = ByteArrayOutputStream()
        override fun getAlgorithmIdentifier(): AlgorithmIdentifier = sigAlgID
        override fun getOutputStream(): OutputStream = baos
        override fun getSignature(): ByteArray = sign(publicKey, baos.toByteArray()).bytes
    }
}

private fun getValidityWindow(before: Duration, after: Duration): Pair<Date, Date> {
    val startOfDayUTC = Instant.now().truncatedTo(ChronoUnit.DAYS)
    val notBefore = startOfDayUTC - before
    val notAfter = startOfDayUTC + after
    return Pair(Date(notBefore.toEpochMilli()), Date(notAfter.toEpochMilli()))
}

private fun X509CertificateHolder.toJca(): X509Certificate =
    requireNotNull(CertificateFactory.getInstance("X.509").generateCertificate(encoded.inputStream()) as? X509Certificate) {
        "Not an X.509 certificate: $this"
    }

