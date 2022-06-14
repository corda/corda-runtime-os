package net.corda.crypto.impl.schememetadata

import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256K1_TEMPLATE
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_TEMPLATE
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_TEMPLATE
import net.corda.v5.cipher.suite.schemes.GOST3410_GOST3411_TEMPLATE
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.cipher.suite.schemes.KeySchemeTemplate
import net.corda.v5.cipher.suite.schemes.RSA_TEMPLATE
import net.corda.v5.cipher.suite.schemes.SM2_TEMPLATE
import net.corda.v5.cipher.suite.schemes.SPHINCS256_TEMPLATE
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.ECDSA_SHA256_SIGNATURE_SPEC
import net.corda.v5.crypto.ECDSA_SHA384_SIGNATURE_SPEC
import net.corda.v5.crypto.ECDSA_SHA512_SIGNATURE_SPEC
import net.corda.v5.crypto.EDDSA_ED25519_SIGNATURE_SPEC
import net.corda.v5.crypto.GOST3410_GOST3411_SIGNATURE_SPEC
import net.corda.v5.crypto.RSA_SHA256_SIGNATURE_SPEC
import net.corda.v5.crypto.RSA_SHA384_SIGNATURE_SPEC
import net.corda.v5.crypto.RSA_SHA512_SIGNATURE_SPEC
import net.corda.v5.crypto.SM2_SHA256_SIGNATURE_SPEC
import net.corda.v5.crypto.SM2_SM3_SIGNATURE_SPEC
import net.corda.v5.crypto.SPHINCS256_SHA512_SIGNATURE_SPEC
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
    val signatureSpecMap: Map<DigestAlgorithmName, SignatureSpec>
) {
    constructor(
        provider: Provider,
        template: KeySchemeTemplate,
        signatureNameMap: Map<DigestAlgorithmName, SignatureSpec>
    ) :
            this(template.makeScheme(provider.name), signatureNameMap)

    fun getSignatureSpec(digest: DigestAlgorithmName): SignatureSpec? =
        signatureSpecMap[digest]
}

class RSAKeySchemeInfo(
    provider: Provider
) : KeySchemeInfo(provider, RSA_TEMPLATE, mapOf(
        DigestAlgorithmName.SHA2_256 to RSA_SHA256_SIGNATURE_SPEC,
        DigestAlgorithmName.SHA2_384 to RSA_SHA384_SIGNATURE_SPEC,
        DigestAlgorithmName.SHA2_512 to RSA_SHA512_SIGNATURE_SPEC
    )
)

abstract class ECDSAKeySchemeInfo(
    provider: Provider,
    template: KeySchemeTemplate
) : KeySchemeInfo(provider, template, mapOf(
        DigestAlgorithmName.SHA2_256 to ECDSA_SHA256_SIGNATURE_SPEC,
        DigestAlgorithmName.SHA2_384 to ECDSA_SHA384_SIGNATURE_SPEC,
        DigestAlgorithmName.SHA2_512 to ECDSA_SHA512_SIGNATURE_SPEC
    )
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
        DigestAlgorithmName("NONE") to EDDSA_ED25519_SIGNATURE_SPEC
    )
)

class SM2KeySchemeInfo(
    provider: Provider
) : KeySchemeInfo(
    provider, SM2_TEMPLATE, mapOf(
        DigestAlgorithmName("SM3") to SM2_SM3_SIGNATURE_SPEC,
        DigestAlgorithmName.SHA2_256 to SM2_SHA256_SIGNATURE_SPEC
    )
)

class GOST3410GOST3411KeySchemeInfo(
    provider: Provider
) : KeySchemeInfo(
    provider, GOST3410_GOST3411_TEMPLATE, mapOf(
        DigestAlgorithmName("GOST3411") to GOST3410_GOST3411_SIGNATURE_SPEC
    )
)

class SPHINCS256KeySchemeInfo(
    provider: Provider
) : KeySchemeInfo(
    provider, SPHINCS256_TEMPLATE, mapOf(
        DigestAlgorithmName.SHA2_512 to SPHINCS256_SHA512_SIGNATURE_SPEC
    )
)