package net.corda.crypto.softhsm

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.cipher.suite.schemes.KeySchemeCapability
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.GOST3410_GOST3411_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.RSA_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.SM2_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.SPHINCS256_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.X25519_CODE_NAME
import net.corda.v5.crypto.SignatureSpec

private val SUPPORTED_SCHEMES: Map<String, List<SignatureSpec>> = mapOf(
    RSA_CODE_NAME to listOf(
        SignatureSpecs.RSA_SHA256,
        SignatureSpecs.RSA_SHA384,
        SignatureSpecs.RSA_SHA512,
        SignatureSpecs.RSASSA_PSS_SHA256,
        SignatureSpecs.RSASSA_PSS_SHA384,
        SignatureSpecs.RSASSA_PSS_SHA512,
        SignatureSpecs.RSA_SHA256_WITH_MGF1,
        SignatureSpecs.RSA_SHA384_WITH_MGF1,
        SignatureSpecs.RSA_SHA512_WITH_MGF1
    ),
    ECDSA_SECP256K1_CODE_NAME to listOf(
        SignatureSpecs.ECDSA_SHA256,
        SignatureSpecs.ECDSA_SHA384,
        SignatureSpecs.ECDSA_SHA512
    ),
    ECDSA_SECP256R1_CODE_NAME to listOf(
        SignatureSpecs.ECDSA_SHA256,
        SignatureSpecs.ECDSA_SHA384,
        SignatureSpecs.ECDSA_SHA512
    ),
    EDDSA_ED25519_CODE_NAME to listOf(SignatureSpecs.EDDSA_ED25519),
    X25519_CODE_NAME to emptyList(),
    SPHINCS256_CODE_NAME to listOf(SignatureSpecs.SPHINCS256_SHA512),
    SM2_CODE_NAME to listOf(SignatureSpecs.SM2_SM3),
    GOST3410_GOST3411_CODE_NAME to listOf(SignatureSpecs.GOST3410_GOST3411)
)

fun deriveSupportedSchemes(schemeMetadata: CipherSchemeMetadata): Map<KeyScheme, List<SignatureSpec>> =
    SUPPORTED_SCHEMES
        .filter { (codeName, _) -> schemeMetadata.schemes.any { it.codeName == codeName } }
        .entries.associate { (codeName, signatureSpecs) ->
            val scheme = schemeMetadata.findKeyScheme(codeName)
            if (signatureSpecs.isNotEmpty()) {
                require(scheme.canDo(KeySchemeCapability.SIGN)) {
                    "Key scheme '${scheme.codeName}' cannot be used for signing."
                }
            }
            scheme to signatureSpecs
        }
