package net.corda.crypto.tck.testing.hsms

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.CustomSignatureSpec
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.cipher.suite.SigningSpec
import net.corda.crypto.cipher.suite.getParamsSafely
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.impl.SignatureInstances
import net.corda.crypto.impl.getSigningData
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.PrivateKey
import java.security.Provider
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher

abstract class AbstractHSM(
    userName: String,
    protected val schemeMetadata: CipherSchemeMetadata,
    private val digestService: PlatformDigestService
) {
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    protected val masterKeys = ConcurrentHashMap<String, WrappingKey>()

    protected val supportedSchemesMap = produceSupportedSchemes(schemeMetadata, mapOf(
        RSA_CODE_NAME to listOf(
            SignatureSpec.RSA_SHA256,
            SignatureSpec.RSASSA_PSS_SHA256
        ),
        ECDSA_SECP256R1_CODE_NAME to listOf(
            SignatureSpec.ECDSA_SHA256
        ),
        EDDSA_ED25519_CODE_NAME to listOf(
            SignatureSpec.EDDSA_ED25519
        )
    ))

    private val signatureInstances = SignatureInstances(schemeMetadata.providers)

    init {
        logger.info("Created ${this::class.simpleName} for configured user: $userName")
    }

    protected fun sign(spec: SigningSpec, privateKey: PrivateKey, data: ByteArray): ByteArray {
        require (isSupported(spec.keyScheme)) {
            "Unsupported signature scheme: ${spec.keyScheme.codeName}"
        }
        require (data.isNotEmpty()) {
            "Signing of an empty array is not permitted."
        }
        val signingData = spec.signatureSpec.getSigningData(digestService, data)
        val signatureBytes = if (spec.signatureSpec is CustomSignatureSpec && spec.keyScheme.algorithmName == "RSA") {
            // when the hash is precalculated and the key is RSA the actual sign operation is encryption
            val cipher = Cipher.getInstance(spec.signatureSpec.signatureName, provider(spec.keyScheme))
            cipher.init(Cipher.ENCRYPT_MODE, privateKey)
            cipher.doFinal(signingData)
        } else {
            signatureInstances.withSignature(spec.keyScheme, spec.signatureSpec) { signature ->
                spec.signatureSpec.getParamsSafely()?.let { params -> signature.setParameter(params) }
                signature.initSign(privateKey, schemeMetadata.secureRandom)
                signature.update(signingData)
                signature.sign()
            }
        }
        return signatureBytes
    }

    protected fun isSupported(scheme: KeyScheme): Boolean = supportedSchemesMap.containsKey(scheme)

    protected fun provider(scheme: KeyScheme): Provider = schemeMetadata.providers.getValue(scheme.providerName)

    private fun produceSupportedSchemes(
        schemeMetadata: CipherSchemeMetadata,
        supported: Map<String, List<SignatureSpec>>
    ): Map<KeyScheme, List<SignatureSpec>> =
        mutableMapOf<KeyScheme, List<SignatureSpec>>().apply {
            supported.forEach {
                addIfSupported(schemeMetadata, it.key, it.value)
            }
        }

    private fun MutableMap<KeyScheme, List<SignatureSpec>>.addIfSupported(
        schemeMetadata: CipherSchemeMetadata,
        codeName: String,
        signatures: List<SignatureSpec>
    ) {
        if (schemeMetadata.schemes.any { it.codeName == codeName }) {
            put(schemeMetadata.findKeyScheme(codeName), signatures)
        }
    }
}