package net.corda.crypto.impl

import net.corda.crypto.cipher.suite.schemes.ECDSA_SECP256K1_TEMPLATE
import net.corda.crypto.cipher.suite.schemes.ECDSA_SECP256R1_TEMPLATE
import net.corda.crypto.cipher.suite.schemes.EDDSA_ED25519_TEMPLATE
import net.corda.crypto.cipher.suite.schemes.GOST3410_GOST3411_TEMPLATE
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.cipher.suite.schemes.KeySchemeTemplate
import net.corda.crypto.cipher.suite.schemes.RSA_TEMPLATE
import net.corda.crypto.cipher.suite.schemes.SM2_TEMPLATE
import net.corda.crypto.cipher.suite.schemes.SPHINCS256_TEMPLATE
import net.corda.crypto.cipher.suite.schemes.X25519_TEMPLATE
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec
import java.security.Provider

/**
 * Defines the [KeyScheme] and which [SignatureSpec]s can be inferred to be used for the schemes using
 * the digest algorithm.
 *
 * See https://www.bouncycastle.org/specifications.html to get list of supported signatures.
 * Scroll down to "Signature Algorithms" / "Schemes" (or search for "SHA256withECDDSA")
 */
abstract class KeySchemeInfo private constructor(
    val scheme: KeyScheme,
    val digestToSignatureSpecMap: Map<DigestAlgorithmName, SignatureSpec>,
    val defaultSignatureSpec: SignatureSpec?
) {
    constructor(
        provider: Provider,
        template: KeySchemeTemplate,
        signatureNameMap: Map<DigestAlgorithmName, SignatureSpec>,
        defaultSignatureSpec: SignatureSpec?
    ) : this(template.makeScheme(provider.name), signatureNameMap, defaultSignatureSpec)

    fun getSignatureSpec(digest: DigestAlgorithmName): SignatureSpec? = digestToSignatureSpecMap[digest]
}

class RSAKeySchemeInfo(
    provider: Provider
) : KeySchemeInfo(
    provider, RSA_TEMPLATE, mapOf(
        DigestAlgorithmName.SHA2_256 to SignatureSpec.RSA_SHA256,
        DigestAlgorithmName.SHA2_384 to SignatureSpec.RSA_SHA384,
        DigestAlgorithmName.SHA2_512 to SignatureSpec.RSA_SHA512
    ),
    SignatureSpec.RSA_SHA256
)

abstract class ECDSAKeySchemeInfo(
    provider: Provider, template: KeySchemeTemplate
) : KeySchemeInfo(
    provider, template, mapOf(
        DigestAlgorithmName.SHA2_256 to SignatureSpec.ECDSA_SHA256,
        DigestAlgorithmName.SHA2_384 to SignatureSpec.ECDSA_SHA384,
        DigestAlgorithmName.SHA2_512 to SignatureSpec.ECDSA_SHA512
    ),
    SignatureSpec.ECDSA_SHA256
)

class ECDSAR1KeySchemeInfo(
    provider: Provider
) : ECDSAKeySchemeInfo(provider, ECDSA_SECP256R1_TEMPLATE)

class ECDSAK1KeySchemeInfo(
    provider: Provider
) : ECDSAKeySchemeInfo(provider, ECDSA_SECP256K1_TEMPLATE)

class EDDSAKeySchemeInfo(
    provider: Provider
) : KeySchemeInfo(
    provider, EDDSA_ED25519_TEMPLATE, mapOf(
        DigestAlgorithmName("NONE") to SignatureSpec.EDDSA_ED25519
    ),
    SignatureSpec.EDDSA_ED25519
)

class X25519KeySchemeInfo(
    provider: Provider
) : KeySchemeInfo(
    provider, X25519_TEMPLATE, emptyMap(), null)

class SM2KeySchemeInfo(
    provider: Provider
) : KeySchemeInfo(
    provider, SM2_TEMPLATE, mapOf(
        DigestAlgorithmName("SM3") to SignatureSpec.SM2_SM3,
        DigestAlgorithmName.SHA2_256 to SignatureSpec.SM2_SHA256
    ),
    SignatureSpec.SM2_SM3
)

class GOST3410GOST3411KeySchemeInfo(
    provider: Provider
) : KeySchemeInfo(
    provider, GOST3410_GOST3411_TEMPLATE, mapOf(
        DigestAlgorithmName("GOST3411") to SignatureSpec.GOST3410_GOST3411
    ),
    SignatureSpec.GOST3410_GOST3411
)

class SPHINCS256KeySchemeInfo(
    provider: Provider
) : KeySchemeInfo(
    provider, SPHINCS256_TEMPLATE, mapOf(
        DigestAlgorithmName.SHA2_512 to SignatureSpec.SPHINCS256_SHA512
    ),
    SignatureSpec.SPHINCS256_SHA512
)