@file:JvmName("CryptoTestUtils")

package net.corda.v5.crypto.mocks

import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.OID_COMPOSITE_KEY
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.sec.SECObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.Time
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.jcajce.spec.EdDSAParameterSpec
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentSigner
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.X509EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

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

private val bouncyCastleProvider: Provider = BouncyCastleProvider()

val specs: Map<AlgorithmIdentifier, KeySpec> = mapOf(
    AlgorithmIdentifier(ASN1ObjectIdentifier("1.3.101.112"), null) to EDDSA_ED25519_SPEC,
    AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, SECObjectIdentifiers.secp256r1) to ECDSA_SECP256R1_SPEC,
    AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, SECObjectIdentifiers.secp256k1) to ECDSA_SECP256K1_SPEC,
    AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, null) to RSA_SPEC
)

fun generateKeyPair(spec: KeySpec): KeyPair {
    val keyPairGenerator = KeyPairGenerator.getInstance(spec.name, bouncyCastleProvider)
    if (spec.spec != null) {
        keyPairGenerator.initialize(spec.spec)
    } else if (spec.keyLength != null) {
        keyPairGenerator.initialize(spec.keyLength)
    }
    return keyPairGenerator.generateKeyPair()
}

fun decodePublicKey(encodedKey: ByteArray): PublicKey {
    val subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(encodedKey)
    return if (subjectPublicKeyInfo.algorithm.algorithm.id == OID_COMPOSITE_KEY) {
        CompositeKey.getInstance(ASN1Primitive.fromByteArray(encodedKey)) {
            decodePublicKey(it)
        }
    } else {
        val mapKey = specs.keys.first { it.algorithm.id == subjectPublicKeyInfo.algorithm.algorithm.id }
        val keyFactory = KeyFactory.getInstance(specs.getValue(mapKey).name, bouncyCastleProvider)
        keyFactory.generatePublic(X509EncodedKeySpec(encodedKey))
    }
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
