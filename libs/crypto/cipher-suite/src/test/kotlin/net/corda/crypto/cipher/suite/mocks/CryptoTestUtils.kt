@file:JvmName("CryptoTestUtils")

package net.corda.crypto.cipher.suite.mocks

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.sec.SECObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.bouncycastle.jcajce.spec.EdDSAParameterSpec
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Provider

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