package net.corda.crypto.softhsm.impl

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.CryptoServiceExtensions
import net.corda.crypto.cipher.suite.CustomSignatureSpec
import net.corda.crypto.cipher.suite.GeneratedKey
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.cipher.suite.SharedSecretSpec
import net.corda.crypto.cipher.suite.SharedSecretWrappedSpec
import net.corda.crypto.cipher.suite.SigningSpec
import net.corda.crypto.cipher.suite.SigningWrappedSpec
import net.corda.crypto.cipher.suite.getParamsSafely
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.cipher.suite.schemes.KeySchemeCapability
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.hes.core.impl.deriveDHSharedSecret
import net.corda.crypto.impl.SignatureInstances
import net.corda.crypto.impl.getSigningData
import net.corda.crypto.softhsm.SoftKeyMap
import net.corda.crypto.softhsm.SoftWrappingKeyMap
import net.corda.v5.base.util.debug
import net.corda.v5.crypto.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.GOST3410_GOST3411_CODE_NAME
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.SM2_CODE_NAME
import net.corda.v5.crypto.SPHINCS256_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.X25519_CODE_NAME
import org.slf4j.LoggerFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Provider
import javax.crypto.Cipher

class SoftCryptoService(
    private val keyMap: SoftKeyMap,
    private val wrappingKeyMap: SoftWrappingKeyMap,
    private val schemeMetadata: CipherSchemeMetadata,
    private val digestService: PlatformDigestService
) : CryptoService {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        fun produceSupportedSchemes(schemeMetadata: CipherSchemeMetadata): Map<KeyScheme, List<SignatureSpec>> =
            mutableMapOf<KeyScheme, List<SignatureSpec>>().apply {
                addIfSupported(
                    schemeMetadata, RSA_CODE_NAME, listOf(
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
                )
                addIfSupported(
                    schemeMetadata, ECDSA_SECP256K1_CODE_NAME, listOf(
                        SignatureSpec.ECDSA_SHA256,
                        SignatureSpec.ECDSA_SHA384,
                        SignatureSpec.ECDSA_SHA512
                    )
                )
                addIfSupported(
                    schemeMetadata, ECDSA_SECP256R1_CODE_NAME, listOf(
                        SignatureSpec.ECDSA_SHA256,
                        SignatureSpec.ECDSA_SHA384,
                        SignatureSpec.ECDSA_SHA512
                    )
                )
                addIfSupported(
                    schemeMetadata, EDDSA_ED25519_CODE_NAME, listOf(
                        SignatureSpec.EDDSA_ED25519
                    )
                )
                addIfSupported(schemeMetadata, X25519_CODE_NAME, emptyList())
                addIfSupported(
                    schemeMetadata, SPHINCS256_CODE_NAME, listOf(
                        SignatureSpec.SPHINCS256_SHA512
                    )
                )
                addIfSupported(
                    schemeMetadata, SM2_CODE_NAME, listOf(
                        SignatureSpec.SM2_SM3
                    )
                )
                addIfSupported(
                    schemeMetadata, GOST3410_GOST3411_CODE_NAME, listOf(
                        SignatureSpec.GOST3410_GOST3411
                    )
                )
            }

        private fun MutableMap<KeyScheme, List<SignatureSpec>>.addIfSupported(
            schemeMetadata: CipherSchemeMetadata,
            codeName: String,
            signatures: List<SignatureSpec>
        ) {
            if (schemeMetadata.schemes.any { it.codeName == codeName }) {
                val scheme = schemeMetadata.findKeyScheme(codeName)
                if(signatures.isNotEmpty()) {
                    require(scheme.canDo(KeySchemeCapability.SIGN)) {
                        "Key scheme '${scheme.codeName}' cannot be used for signing."
                    }
                }
                put(scheme, signatures)
            }
        }
    }

    private val signatureInstances = SignatureInstances(schemeMetadata.providers)

    override val supportedSchemes = produceSupportedSchemes(schemeMetadata)

    override val extensions = listOf(CryptoServiceExtensions.REQUIRE_WRAPPING_KEY)

    override fun createWrappingKey(masterKeyAlias: String, failIfExists: Boolean, context: Map<String, String>) {
        logger.info("createWrappingKey(masterKeyAlias={}, failIfExists={})", masterKeyAlias, failIfExists)
        val wrappingKey = WrappingKey.generateWrappingKey(schemeMetadata)
        if (wrappingKeyMap.exists(masterKeyAlias)) {
            if (failIfExists) {
                throw IllegalStateException("There is an existing key with the alias: $masterKeyAlias")
            } else {
                logger.info(
                    "Wrapping with alias '$masterKeyAlias' already exists, " +
                            "continue as normal as failIfExists is false"
                )
            }
        } else {
            wrappingKeyMap.putWrappingKey(masterKeyAlias, wrappingKey)
        }
    }

    override fun delete(alias: String, context: Map<String, String>): Boolean =
        throw UnsupportedOperationException("The service does not support key deletion.")

    override fun deriveSharedSecret(
        spec: SharedSecretSpec,
        context: Map<String, String>
    ): ByteArray {
        require(spec is SharedSecretWrappedSpec) {
            "The service supports only ${SharedSecretWrappedSpec::class.java}"
        }
        val otherPublicKeyScheme = schemeMetadata.findKeyScheme(spec.otherPublicKey)
        require(spec.keyScheme.canDo(KeySchemeCapability.SHARED_SECRET_DERIVATION)) {
            "The key scheme '${spec.keyScheme}' must support the Diffie–Hellman key agreement."
        }
        require(spec.keyScheme == otherPublicKeyScheme) {
            "The keys must use the same key scheme, publicKey=${spec.keyScheme}, otherPublicKey=$otherPublicKeyScheme"
        }
        logger.info("deriveSharedSecret(spec={})", spec)
        val provider = schemeMetadata.providers.getValue(spec.keyScheme.providerName)
        return deriveDHSharedSecret(
            provider,
            keyMap.getPrivateKey(spec.publicKey, spec.keyMaterialSpec),
            spec.otherPublicKey
        )
    }

    override fun generateKeyPair(spec: KeyGenerationSpec, context: Map<String, String>): GeneratedKey {
        require(supportedSchemes.containsKey(spec.keyScheme)) {
            "Unsupported key scheme: ${spec.keyScheme.codeName}"
        }
        logger.info(
            "generateKeyPair(alias={},masterKeyAlias={},scheme={})",
            spec.alias,
            spec.masterKeyAlias,
            spec.keyScheme.codeName
        )
        val keyPairGenerator = KeyPairGenerator.getInstance(
            spec.keyScheme.algorithmName,
            providerFor(spec.keyScheme)
        )
        if (spec.keyScheme.algSpec != null) {
            keyPairGenerator.initialize(spec.keyScheme.algSpec, schemeMetadata.secureRandom)
        } else if (spec.keyScheme.keySize != null) {
            keyPairGenerator.initialize(spec.keyScheme.keySize!!, schemeMetadata.secureRandom)
        }
        val keyPair = keyPairGenerator.generateKeyPair()
        val privateKeyMaterial = keyMap.wrapPrivateKey(keyPair, spec.masterKeyAlias)
        return GeneratedWrappedKey(
            publicKey = keyPair.public,
            keyMaterial = privateKeyMaterial.keyMaterial,
            encodingVersion = privateKeyMaterial.encodingVersion
        )
    }

    override fun sign(spec: SigningSpec, data: ByteArray, context: Map<String, String>): ByteArray {
        require(spec is SigningWrappedSpec) {
            "The service supports only ${SigningWrappedSpec::class.java}"
        }
        require(data.isNotEmpty()) {
            "Signing of an empty array is not permitted."
        }
        require(supportedSchemes.containsKey(spec.keyScheme)) {
            "Unsupported key scheme: ${spec.keyScheme.codeName}"
        }
        require(spec.keyScheme.canDo(KeySchemeCapability.SIGN)) {
            "Key scheme: ${spec.keyScheme.codeName} cannot be used for signing."
        }
        logger.debug { "sign(spec=$spec)" }
        return sign(spec, keyMap.getPrivateKey(spec.publicKey, spec.keyMaterialSpec), data)
    }

    private fun sign(spec: SigningSpec, privateKey: PrivateKey, data: ByteArray): ByteArray {
        val signingData = spec.signatureSpec.getSigningData(digestService, data)
        val signatureBytes = if (spec.signatureSpec is CustomSignatureSpec && spec.keyScheme.algorithmName == "RSA") {
            // when the hash is precalculated and the key is RSA the actual sign operation is encryption
            val cipher = Cipher.getInstance(spec.signatureSpec.signatureName, providerFor(spec.keyScheme))
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

    private fun providerFor(scheme: KeyScheme): Provider = schemeMetadata.providers.getValue(scheme.providerName)
}
