package net.corda.membership.p2p.helpers

import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.GOST3410_GOST3411_CODE_NAME
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.SM2_CODE_NAME
import net.corda.v5.crypto.SPHINCS256_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.publicKeyId
import net.corda.virtualnode.ShortHash
import java.security.PublicKey

class KeySpecExtractor(
    private val tenantId: String,
    private val cryptoOpsClient: CryptoOpsClient,
) {
    companion object {
        val CryptoSigningKey.spec: SignatureSpec?
            get() = defaultCodeNameToSpec[schemeCodeName]

        private val defaultCodeNameToSpec = mapOf(
            ECDSA_SECP256K1_CODE_NAME to SignatureSpec.ECDSA_SHA256,
            ECDSA_SECP256R1_CODE_NAME to SignatureSpec.ECDSA_SHA256,
            EDDSA_ED25519_CODE_NAME to SignatureSpec.EDDSA_ED25519,
            GOST3410_GOST3411_CODE_NAME to SignatureSpec.GOST3410_GOST3411,
            RSA_CODE_NAME to SignatureSpec.RSA_SHA512,
            SM2_CODE_NAME to SignatureSpec.SM2_SM3,
            SPHINCS256_CODE_NAME to SignatureSpec.SPHINCS256_SHA512,
        )
        private val validSpecsNames = mapOf(
            ECDSA_SECP256K1_CODE_NAME to listOf(
                SignatureSpec.ECDSA_SHA256,
                SignatureSpec.ECDSA_SHA384,
                SignatureSpec.ECDSA_SHA512,
            )
                .map { it.signatureName },
            ECDSA_SECP256R1_CODE_NAME to listOf(
                SignatureSpec.ECDSA_SHA256,
                SignatureSpec.ECDSA_SHA384,
                SignatureSpec.ECDSA_SHA512,
            )
                .map { it.signatureName },
            EDDSA_ED25519_CODE_NAME to listOf(SignatureSpec.EDDSA_ED25519.signatureName),
            GOST3410_GOST3411_CODE_NAME to listOf(SignatureSpec.GOST3410_GOST3411.signatureName),
            RSA_CODE_NAME to listOf(SignatureSpec.RSA_SHA256, SignatureSpec.RSA_SHA384, SignatureSpec.RSA_SHA512)
                .map { it.signatureName },
            SM2_CODE_NAME to listOf(SignatureSpec.SM2_SM3.signatureName),
            SPHINCS256_CODE_NAME to listOf(SignatureSpec.SPHINCS256_SHA512.signatureName),
        )

        fun CryptoSigningKey.validateSpecName(specName: String) {
            val validSpecs = validSpecsNames[this.schemeCodeName]
                ?: throw IllegalArgumentException("Could not identify spec for key scheme ${this.schemeCodeName}.")
            if (!validSpecs.contains(specName)) {
                throw IllegalArgumentException(
                    "Invalid key spec $specName. Valid specs for key scheme ${this.schemeCodeName} are $validSpecs."
                )
            }
        }
    }

    fun getSpec(publicKey: PublicKey): SignatureSpec {
        val keyInfo = cryptoOpsClient.lookupKeysByShortIds(
            tenantId,
            listOf(
                ShortHash.of(publicKey.publicKeyId())
            ),
        ).firstOrNull() ?: throw CordaRuntimeException("Public key is not owned by $tenantId")
        return keyInfo.spec ?: throw CordaRuntimeException("Can not find spec for ${keyInfo.schemeCodeName}")
    }
}
