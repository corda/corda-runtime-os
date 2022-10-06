package net.corda.cipher.suite.impl.platform

import net.corda.v5.cipher.suite.scheme.KeyScheme
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec

/**
 * Defines the [KeyScheme] and which [SignatureSpec]s can be inferred to be used for the schemes using
 * the digest algorithm.
 *
 * See https://www.bouncycastle.org/specifications.html to get list of supported signatures.
 * Scroll down to "Signature Algorithms" / "Schemes" (or search for "SHA256withECDDSA")
 */
abstract class KeySchemeInfo(
    val scheme: KeyScheme,
    val digestToSignatureSpecMap: Map<DigestAlgorithmName, SignatureSpec>,
    val defaultSignatureSpec: SignatureSpec?
)

class RSAKeySchemeInfo : KeySchemeInfo(
    RSA,
    mapOf(
        DigestAlgorithmName.SHA2_256 to SignatureSpec.RSA_SHA256,
        DigestAlgorithmName.SHA2_384 to SignatureSpec.RSA_SHA384,
        DigestAlgorithmName.SHA2_512 to SignatureSpec.RSA_SHA512
    ),
    SignatureSpec.RSA_SHA256
)

abstract class ECDSAKeySchemeInfo(
    scheme: KeyScheme
) : KeySchemeInfo(
    scheme,
    mapOf(
        DigestAlgorithmName.SHA2_256 to SignatureSpec.ECDSA_SHA256,
        DigestAlgorithmName.SHA2_384 to SignatureSpec.ECDSA_SHA384,
        DigestAlgorithmName.SHA2_512 to SignatureSpec.ECDSA_SHA512
    ),
    SignatureSpec.ECDSA_SHA256
)

class ECDSAR1KeySchemeInfo : ECDSAKeySchemeInfo(ECDSA_SECP256R1)

class ECDSAK1KeySchemeInfo : ECDSAKeySchemeInfo(ECDSA_SECP256K1)

class EDDSAKeySchemeInfo : KeySchemeInfo(
    EDDSA_ED25519,
    mapOf(
        DigestAlgorithmName("NONE") to SignatureSpec.EDDSA_ED25519
    ),
    SignatureSpec.EDDSA_ED25519
)

class X25519KeySchemeInfo : KeySchemeInfo(
    X25519, emptyMap(), null
)

class SM2KeySchemeInfo : KeySchemeInfo(
    SM2,
    mapOf(
        DigestAlgorithmName("SM3") to SignatureSpec.SM2_SM3,
        DigestAlgorithmName.SHA2_256 to SignatureSpec.SM2_SHA256
    ),
    SignatureSpec.SM2_SM3
)

class GOST3410GOST3411KeySchemeInfo : KeySchemeInfo(
    GOST3410_GOST3411,
    mapOf(
        DigestAlgorithmName("GOST3411") to SignatureSpec.GOST3410_GOST3411
    ),
    SignatureSpec.GOST3410_GOST3411
)

class SPHINCS256KeySchemeInfo : KeySchemeInfo(
    SPHINCS256,
    mapOf(
        DigestAlgorithmName.SHA2_512 to SignatureSpec.SPHINCS256_SHA512
    ),
    SignatureSpec.SPHINCS256_SHA512
)