package net.corda.crypto.tck.testing.hsms

import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.impl.SignatureInstances
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.SigningSpec
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.GOST3410_GOST3411_CODE_NAME
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.SM2_CODE_NAME
import net.corda.v5.crypto.SPHINCS256_CODE_NAME
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import java.security.PrivateKey
import java.security.Provider
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher

abstract class AbstractHSM(
    supportedSchemeCodes: List<String>,
    protected val schemeMetadata: CipherSchemeMetadata,
    protected val digestService: DigestService
) {
    protected val masterKeys = ConcurrentHashMap<String, WrappingKey>()

    protected val supportedSchemes = produceSupportedSchemes(schemeMetadata, supportedSchemeCodes)

    private val signatureInstances = SignatureInstances(schemeMetadata.providers)

    protected fun sign(spec: SigningSpec, privateKey: PrivateKey, data: ByteArray): ByteArray {
        if (!isSupported(spec.keyScheme)) {
            throw CryptoServiceBadRequestException("Unsupported signature scheme: ${spec.keyScheme.codeName}")
        }
        if (data.isEmpty()) {
            throw CryptoServiceBadRequestException("Signing of an empty array is not permitted.")
        }
        val signatureBytes = if (spec.signatureSpec.precalculateHash && spec.keyScheme.algorithmName == "RSA") {
            // when the hash is precalculated and the key is RSA the actual sign operation is encryption
            val cipher = Cipher.getInstance(spec.signatureSpec.signatureName, provider(spec.keyScheme))
            cipher.init(Cipher.ENCRYPT_MODE, privateKey)
            val signingData = spec.signatureSpec.getSigningData(digestService, data)
            cipher.doFinal(signingData)
        } else {
            signatureInstances.withSignature(spec.keyScheme, spec.signatureSpec) {
                if (spec.signatureSpec.params != null) {
                    it.setParameter(spec.signatureSpec.params)
                }
                it.initSign(privateKey, schemeMetadata.secureRandom)
                val signingData = spec.signatureSpec.getSigningData(digestService, data)
                it.update(signingData)
                it.sign()
            }
        }
        return signatureBytes
    }

    private fun produceSupportedSchemes(schemeMetadata: CipherSchemeMetadata, codes: List<String>): List<KeyScheme> =
        mutableListOf<KeyScheme>().apply {
            codes.forEach {
                addIfSupported(schemeMetadata, it)
            }
        }

    private fun MutableList<KeyScheme>.addIfSupported(
        schemeMetadata: CipherSchemeMetadata,
        codeName: String
    ) {
        if (schemeMetadata.schemes.any { it.codeName == codeName }) {
            add(schemeMetadata.findKeyScheme(codeName))
        }
    }

    private fun isSupported(scheme: KeyScheme): Boolean = supportedSchemes.any { it.codeName == scheme.codeName }

    private fun provider(scheme: KeyScheme): Provider = schemeMetadata.providers.getValue(scheme.providerName)
}