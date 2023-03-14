package net.corda.cipher.suite.impl.infra

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.getParamsSafely
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.GOST3410_GOST3411_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.RSA_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.SM2_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.SPHINCS256_CODE_NAME
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
    signatureSpec.getParamsSafely()?.let { params -> signature.setParameter(params) }
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
        RSA_CODE_NAME -> SignatureSpec.RSA_SHA256
        ECDSA_SECP256R1_CODE_NAME, ECDSA_SECP256K1_CODE_NAME -> SignatureSpec.ECDSA_SHA256
        EDDSA_ED25519_CODE_NAME -> SignatureSpec.EDDSA_ED25519
        SM2_CODE_NAME -> SignatureSpec.SM2_SM3
        GOST3410_GOST3411_CODE_NAME -> SignatureSpec.GOST3410_GOST3411
        SPHINCS256_CODE_NAME -> SignatureSpec.SPHINCS256_SHA512
        else -> throw IllegalArgumentException("Cannot get default signature spec for $codeName")
    }
}

