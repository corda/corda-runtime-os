package net.corda.crypto.softhsm

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.cipher.suite.schemes.KeySchemeCapability
import net.corda.v5.crypto.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.GOST3410_GOST3411_CODE_NAME
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.SM2_CODE_NAME
import net.corda.v5.crypto.SPHINCS256_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.X25519_CODE_NAME


val SUPPORTED_SCHEMES: List<Pair<String, List<SignatureSpec>>> = listOf(
    Pair(
        RSA_CODE_NAME, listOf(
            SignatureSpec.RSA_SHA256,
            SignatureSpec.RSA_SHA384,
            SignatureSpec.RSA_SHA512,
            SignatureSpec.RSASSA_PSS_SHA256,
            SignatureSpec.RSASSA_PSS_SHA384,
            SignatureSpec.RSASSA_PSS_SHA512,
            SignatureSpec.RSA_SHA256_WITH_MGF1,
            SignatureSpec.RSA_SHA384_WITH_MGF1,
            SignatureSpec.RSA_SHA512_WITH_MGF1
        )
    ),
    Pair(
        ECDSA_SECP256K1_CODE_NAME, listOf(
            SignatureSpec.ECDSA_SHA256,
            SignatureSpec.ECDSA_SHA384,
            SignatureSpec.ECDSA_SHA512
        )
    ),
    Pair(
        ECDSA_SECP256R1_CODE_NAME, listOf(
            SignatureSpec.ECDSA_SHA256,
            SignatureSpec.ECDSA_SHA384,
            SignatureSpec.ECDSA_SHA512
        )
    ),
    Pair(EDDSA_ED25519_CODE_NAME, listOf(SignatureSpec.EDDSA_ED25519)),
    Pair(X25519_CODE_NAME, emptyList()),
    Pair(SPHINCS256_CODE_NAME, listOf(SignatureSpec.SPHINCS256_SHA512)),
    Pair(SM2_CODE_NAME, listOf(SignatureSpec.SM2_SM3)),
    Pair(GOST3410_GOST3411_CODE_NAME, listOf(SignatureSpec.GOST3410_GOST3411))
)


fun deriveSupportedSchemes(schemeMetadata: CipherSchemeMetadata): Map<KeyScheme, List<SignatureSpec>> =
    SUPPORTED_SCHEMES
        .filter { combo -> schemeMetadata.schemes.any { it.codeName == combo.first } }
        .map {
            val scheme = schemeMetadata.findKeyScheme(it.first)
            if (it.second.isNotEmpty()) {
                require(scheme.canDo(KeySchemeCapability.SIGN)) {
                    "Key scheme '${scheme.codeName}' cannot be used for signing."
                }
            }
            Pair(scheme, it.second)
        }
        .toMap()
