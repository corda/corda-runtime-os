package net.corda.membership.p2p.helpers

import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.cipher.suite.publicKeyId
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.ShortHash
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.GOST3410_GOST3411_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.RSA_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.SM2_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.SPHINCS256_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

class KeySpecExtractor(
    private val tenantId: String,
    private val cryptoOpsClient: CryptoOpsClient,
) {
    companion object {
        val CryptoSigningKey.spec: SignatureSpec?
            get() = defaultCodeNameToSpec[schemeCodeName]

        private val defaultCodeNameToSpec = mapOf(
            ECDSA_SECP256K1_CODE_NAME to SignatureSpecs.ECDSA_SHA256,
            ECDSA_SECP256R1_CODE_NAME to SignatureSpecs.ECDSA_SHA256,
            EDDSA_ED25519_CODE_NAME to SignatureSpecs.EDDSA_ED25519,
            GOST3410_GOST3411_CODE_NAME to SignatureSpecs.GOST3410_GOST3411,
            RSA_CODE_NAME to SignatureSpecs.RSA_SHA512,
            SM2_CODE_NAME to SignatureSpecs.SM2_SM3,
            SPHINCS256_CODE_NAME to SignatureSpecs.SPHINCS256_SHA512,
        )
        private val validSpecsNamesForSessionKeys = mapOf(
            ECDSA_SECP256K1_CODE_NAME to listOf(
                SignatureSpecs.ECDSA_SHA256,
                SignatureSpecs.ECDSA_SHA384,
                SignatureSpecs.ECDSA_SHA512,
            )
                .map { it.signatureName },
            ECDSA_SECP256R1_CODE_NAME to listOf(
                SignatureSpecs.ECDSA_SHA256,
                SignatureSpecs.ECDSA_SHA384,
                SignatureSpecs.ECDSA_SHA512,
            )
                .map { it.signatureName },
            RSA_CODE_NAME to listOf(SignatureSpecs.RSA_SHA256, SignatureSpecs.RSA_SHA384, SignatureSpecs.RSA_SHA512)
                .map { it.signatureName },
        )
        private val validSpecsNames = mapOf(
            EDDSA_ED25519_CODE_NAME to listOf(SignatureSpecs.EDDSA_ED25519.signatureName),
            GOST3410_GOST3411_CODE_NAME to listOf(SignatureSpecs.GOST3410_GOST3411.signatureName),
            SM2_CODE_NAME to listOf(SignatureSpecs.SM2_SM3.signatureName),
            SPHINCS256_CODE_NAME to listOf(SignatureSpecs.SPHINCS256_SHA512.signatureName),
        ) + validSpecsNamesForSessionKeys

        fun CryptoSigningKey.validateSchemeAndSignatureSpec(specName: String?, type: KeySpecType = KeySpecType.OTHER) {
            val validSpecs = if (type == KeySpecType.SESSION) {
                requireNotNull(validSpecsNamesForSessionKeys[this.schemeCodeName]) {
                    "Invalid key scheme ${this.schemeCodeName}. The following " +
                        "schemes could be used when generating session keys: ${validSpecsNamesForSessionKeys.keys}"
                }
            } else {
                requireNotNull(validSpecsNames[this.schemeCodeName]) {
                    "Invalid key scheme ${this.schemeCodeName}. The following " +
                        "schemes could be used when generating keys: ${validSpecsNames.keys}"
                }
            }
            specName?.let {
                require(validSpecs.contains(it)) {
                    "Invalid key spec $it. Valid specs for key scheme ${this.schemeCodeName} are $validSpecs."
                }
            }
        }
    }

    fun getSpec(publicKey: PublicKey): SignatureSpec {
        val keyInfo = cryptoOpsClient.lookupKeysByIds(
            tenantId,
            listOf(
                ShortHash.of(publicKey.publicKeyId())
            ),
        ).firstOrNull() ?: throw CordaRuntimeException("Public key is not owned by $tenantId")
        return keyInfo.spec ?: throw CordaRuntimeException("Can not find spec for ${keyInfo.schemeCodeName}")
    }

    enum class KeySpecType {
        SESSION, OTHER
    }
}
