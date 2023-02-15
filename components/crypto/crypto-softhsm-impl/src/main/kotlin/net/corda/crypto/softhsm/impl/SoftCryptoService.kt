package net.corda.crypto.softhsm.impl

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.CryptoServiceExtensions
import net.corda.crypto.cipher.suite.CustomSignatureSpec
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.cipher.suite.KeyMaterialSpec
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
import net.corda.crypto.persistence.WrappingKeyStore
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import javax.crypto.Cipher
import com.github.benmanes.caffeine.cache.Cache
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.deriveSupportedSchemes

val WRAPPING_KEY_ENCODING_VERSION: Int = 1
val PRIVATE_KEY_ENCODING_VERSION: Int = 1

/**
 * The heart of the crypto processor.
 *
 * @param wrappingKeyStore which provides save and find operations for wrapping keys.
 * @param schemeMetadata which specifies encryption schemes, digests schemes and a source of randomness

 * This class is all about the business logic of generating, storing and using key pairs; it can be run
 * without a database, without OSGi and without SmartConfig, which makes it easy to test.
 */
open class SoftCryptoService(
    private val wrappingKeyStore: WrappingKeyStore,
    private val schemeMetadata: CipherSchemeMetadata,
    private val rootWrappingKey: WrappingKey,
    private val wrappingKeyCache: Cache<String, WrappingKey>,
    private val privateKeyCache: Cache<PublicKey, PrivateKey>
) : CryptoService {
    private val digestService = PlatformDigestServiceImpl(schemeMetadata)

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val signatureInstances = SignatureInstances(schemeMetadata.providers)

    override val supportedSchemes = deriveSupportedSchemes(schemeMetadata)

    override val extensions = listOf(CryptoServiceExtensions.REQUIRE_WRAPPING_KEY)

    override fun createWrappingKey(masterKeyAlias: String, failIfExists: Boolean, context: Map<String, String>) {
        require(masterKeyAlias != "") { "Alias must not be empty" }
        val cached = wrappingKeyCache.getIfPresent(masterKeyAlias) != null
        val available = if (cached) true else wrappingKeyStore.findWrappingKey(masterKeyAlias) != null
        logger.info("createWrappingKey(alias=$masterKeyAlias failIfExists=$failIfExists) cached=$cached available=$available")
        if (available) {
            if (failIfExists) throw IllegalStateException("There is an existing key with the alias: $masterKeyAlias")
            logger.info("Not creating wrapping key for '$masterKeyAlias' since a key is available")
            return
        }
        val wrappingKey = WrappingKey.generateWrappingKey(schemeMetadata)
        val wrappedKeyMaterial = rootWrappingKey.wrap(wrappingKey)
        val wrappingKeyInfo =
            WrappingKeyInfo(WRAPPING_KEY_ENCODING_VERSION, wrappingKey.algorithm, wrappedKeyMaterial)
        wrappingKeyStore.saveWrappingKey(masterKeyAlias, wrappingKeyInfo)
        wrappingKeyCache.put(masterKeyAlias, wrappingKey)
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
            getPrivateKey(spec.publicKey, spec.keyMaterialSpec),
            spec.otherPublicKey
        )
    }

    // This is pulled out as a separate method so that it can be overriden and test fixtures can count
    // the number of times this was called. TODO - Consider add metrics for wrap and unwrap and using those
    // from the test cases.
    open fun wrapPrivateKey(wrappingKey: WrappingKey, privateKey: PrivateKey): ByteArray = wrappingKey.wrap(privateKey)

    override fun generateKeyPair(spec: KeyGenerationSpec, context: Map<String, String>): GeneratedWrappedKey {
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
        val wrappingKey = getWrappingKey(spec.masterKeyAlias)
        val privateKeyMaterial = wrapPrivateKey(wrappingKey, keyPair.private)
        privateKeyCache.put(keyPair.public, keyPair.private)
        return GeneratedWrappedKey(keyPair.public, privateKeyMaterial, PRIVATE_KEY_ENCODING_VERSION)
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
        return sign(spec, getPrivateKey(spec.publicKey, spec.keyMaterialSpec), data)
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

    fun getWrappingKey(alias: String): WrappingKey = wrappingKeyCache.get(alias) {
        val wrappingKeyInfo =
            wrappingKeyStore.findWrappingKey(alias) ?: throw IllegalStateException("The $alias is not created yet.")
        require(wrappingKeyInfo.encodingVersion == WRAPPING_KEY_ENCODING_VERSION) {
            "Unknown wrapping key encoding. Expected to be $WRAPPING_KEY_ENCODING_VERSION"
        }
        require(rootWrappingKey.algorithm == wrappingKeyInfo.algorithmName) {
            "Expected algorithm is ${rootWrappingKey.algorithm} but was ${wrappingKeyInfo.algorithmName}"
        }
        rootWrappingKey.unwrapWrappingKey(wrappingKeyInfo.keyMaterial)
    }

    // See comment on wrapPrivateKey - also pulled out so it can be counted.
    open fun unwrapPrivateKey(key: WrappingKey, keyMaterial: ByteArray): PrivateKey = key.unwrap(keyMaterial)

    fun getPrivateKey(publicKey: PublicKey, spec: KeyMaterialSpec): PrivateKey =
        privateKeyCache.get(publicKey) {
            unwrapPrivateKey(getWrappingKey(spec.masterKeyAlias), spec.keyMaterial)
        }

    fun wrappingKeyExists(wrappingKeyAlias: String): Boolean {
        if (wrappingKeyCache.getIfPresent(wrappingKeyAlias) != null) return true
        if (wrappingKeyStore.findWrappingKey(wrappingKeyAlias) != null) return true
        return false
    }
}
