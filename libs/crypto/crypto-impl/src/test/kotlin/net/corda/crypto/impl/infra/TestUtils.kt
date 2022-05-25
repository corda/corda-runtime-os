package net.corda.crypto.impl.infra

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.ECDSA_SHA256_SIGNATURE_SPEC
import net.corda.v5.crypto.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.EDDSA_ED25519_NONE_SIGNATURE_SPEC
import net.corda.v5.crypto.GOST3410_GOST3411_CODE_NAME
import net.corda.v5.crypto.GOST3410_GOST3411_SIGNATURE_SPEC
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.RSA_SHA256_SIGNATURE_SPEC
import net.corda.v5.crypto.SM2_CODE_NAME
import net.corda.v5.crypto.SM2_SM3_SIGNATURE_SPEC
import net.corda.v5.crypto.SPHINCS256_CODE_NAME
import net.corda.v5.crypto.SPHINCS256_SHA512_SIGNATURE_SPEC
import net.corda.v5.crypto.SignatureSpec
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.Signature

fun generateKeyPair(schemeMetadata: CipherSchemeMetadata, keySchemeCodeName: String): KeyPair {
    val scheme = schemeMetadata.findKeyScheme(keySchemeCodeName)
    val keyPairGenerator = KeyPairGenerator.getInstance(
        scheme.algorithmName,
        schemeMetadata.providers.getValue(scheme.providerName)
    )
    if (scheme.algSpec != null) {
        keyPairGenerator.initialize(scheme.algSpec, schemeMetadata.secureRandom)
    } else if (scheme.keySize != null) {
        keyPairGenerator.initialize(scheme.keySize!!, schemeMetadata.secureRandom)
    }
    return keyPairGenerator.generateKeyPair()
}

// we are not expected to deal with hash pre-calculation in the tests in this module
fun signData(
    schemeMetadata: CipherSchemeMetadata,
    signatureSpec: SignatureSpec,
    keyPair: KeyPair,
    data: ByteArray
): ByteArray {
    val scheme = schemeMetadata.findKeyScheme(keyPair.public)
    val signature = Signature.getInstance(
        signatureSpec.signatureName,
        schemeMetadata.providers[scheme.providerName]
    )
    if(signatureSpec.params != null) {
        signature.setParameter(signatureSpec.params)
    }
    signature.initSign(keyPair.private, schemeMetadata.secureRandom)
    signature.update(data)
    return signature.sign()
}

fun CipherSchemeMetadata.inferSignatureSpecOrCreateDefault(publicKey: PublicKey, digest: DigestAlgorithmName): SignatureSpec {
    val inferred = inferSignatureSpec(publicKey, digest)
    if(inferred != null) {
        return inferred
    }
    return when(val codeName = findKeyScheme(publicKey).codeName) {
        RSA_CODE_NAME -> RSA_SHA256_SIGNATURE_SPEC
        ECDSA_SECP256R1_CODE_NAME, ECDSA_SECP256K1_CODE_NAME -> ECDSA_SHA256_SIGNATURE_SPEC
        EDDSA_ED25519_CODE_NAME -> EDDSA_ED25519_NONE_SIGNATURE_SPEC
        SM2_CODE_NAME -> SM2_SM3_SIGNATURE_SPEC
        GOST3410_GOST3411_CODE_NAME -> GOST3410_GOST3411_SIGNATURE_SPEC
        SPHINCS256_CODE_NAME -> SPHINCS256_SHA512_SIGNATURE_SPEC
        else -> throw IllegalArgumentException("Cannot get default signature spec for $codeName")
    }
}

