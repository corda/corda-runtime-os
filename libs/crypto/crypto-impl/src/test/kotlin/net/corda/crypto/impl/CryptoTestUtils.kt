package net.corda.crypto.impl

import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.Time
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.jcajce.spec.EdDSAParameterSpec
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.operator.ContentSigner
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.AlgorithmParameterSpec
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

fun CompositeKeyProviderImpl.createFromKeys(vararg keys: PublicKey, threshold: Int? = 1) =
    createFromKeys(keys.toList(), threshold)

fun CompositeKeyProviderImpl.createFromWeightedKeys(vararg keys: CompositeKeyNodeAndWeight, threshold: Int? = 1) =
    create(keys.toList(), threshold)

fun X509CertificateHolder.toJca(): X509Certificate =
    requireNotNull(
        CertificateFactory.getInstance("X.509").generateCertificate(encoded.inputStream()) as? X509Certificate
    ) {
        "Not an X.509 certificate: $this"
    }

data class KeySpec(
    val name: String,
    val spec: AlgorithmParameterSpec? = null,
    val keyLength: Int? = null
)

val EDDSA_ED25519_SPEC = KeySpec(
    name = "EdDSA",
    spec = EdDSAParameterSpec(EdDSAParameterSpec.Ed25519)
)

val ECDSA_SECP256R1_SPEC = KeySpec(
    name = "EC",
    spec = ECNamedCurveTable.getParameterSpec("secp256r1")
)

val ECDSA_SECP256K1_SPEC = KeySpec(
    name = "EC",
    spec = ECNamedCurveTable.getParameterSpec("secp256k1")
)

val RSA_SPEC = KeySpec(
    name = "RSA",
    keyLength = 3072
)

fun getValidityWindow(before: Duration, after: Duration): Pair<Date, Date> {
    val startOfDayUTC = Instant.now().truncatedTo(ChronoUnit.DAYS)
    val notBefore = startOfDayUTC - before
    val notAfter = startOfDayUTC + after
    return Pair(Date(notBefore.toEpochMilli()), Date(notAfter.toEpochMilli()))
}

fun createDevCertificate(
    issuer: X500Name,
    contentSigner: ContentSigner,
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
    return v3CertGen.build(contentSigner).toJca()
}

fun getDevSigner(privateKey: PrivateKey, signatureAlgorithm: AlgorithmIdentifier): ContentSigner {
    return object : ContentSigner {
        private val baos = ByteArrayOutputStream()
        override fun getAlgorithmIdentifier(): AlgorithmIdentifier = signatureAlgorithm
        override fun getOutputStream(): OutputStream = baos
        override fun getSignature(): ByteArray =
            Signature.getInstance(signatureAlgorithm.algorithm.id, bouncyCastleProvider).apply {
                initSign(privateKey)
                update(baos.toByteArray())
            }.sign()
    }
}
