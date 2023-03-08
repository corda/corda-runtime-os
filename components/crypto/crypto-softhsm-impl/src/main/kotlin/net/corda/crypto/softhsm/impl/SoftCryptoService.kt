package net.corda.crypto.softhsm.impl

import com.github.benmanes.caffeine.cache.Cache
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.CryptoServiceExtensions
import net.corda.crypto.cipher.suite.CustomSignatureSpec
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.cipher.suite.KeyMaterialSpec
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.cipher.suite.SharedSecretSpec
import net.corda.crypto.cipher.suite.SharedSecretWrappedSpec
import net.corda.crypto.cipher.suite.SigningSpec
import net.corda.crypto.cipher.suite.SigningWrappedSpec
import net.corda.crypto.cipher.suite.getParamsSafely
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.cipher.suite.schemes.KeySchemeCapability
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.core.aes.WrappingKeyImpl
import net.corda.crypto.hes.core.impl.deriveDHSharedSecret
import net.corda.crypto.impl.CordaSecureRandomService.Companion.algorithm
import net.corda.crypto.impl.SignatureInstances
import net.corda.crypto.impl.getSigningData
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.WrappingKeyStore
import net.corda.crypto.softhsm.KeyPairGeneratorFactory
import net.corda.crypto.softhsm.deriveSupportedSchemes
import net.corda.utilities.VisibleForTesting
import net.corda.utilities.debug
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher

const val WRAPPING_KEY_ENCODING_VERSION: Int = 1
const val PRIVATE_KEY_ENCODING_VERSION: Int = 1

/**
 * This class is all about the business logic of generating, storing and using key pairs; it can be run
 * without a database, without OSGi and without SmartConfig, which makes it easy to test.
 *
 * @param wrappingKeyStore which provides save and find operations for wrapping keys.
 * @param schemeMetadata which specifies encryption schemes, digests schemes and a source of randomness
 * @param rootWrappingKey the single top level wrapping key for encrypting all key material at rest
 * @param wrappingKeyCache an optional [Cache] which optimises access to wrapping keys
 * @param privateKeyCache an optional [Cache] which optimises access to private keys
 * @param digestService optionally supply a platform digest service instance; if not one will be constructed
 */


@Suppress("LongParameterList")
class SoftCryptoService(
    private val wrappingKeyStore: WrappingKeyStore,
    private val schemeMetadata: CipherSchemeMetadata,
    private val rootWrappingKey: WrappingKey,
    private val wrappingKeyCache: Cache<String, WrappingKey>? = null,
    private val privateKeyCache: Cache<PublicKey, PrivateKey>? = null,
    private val digestService: PlatformDigestService = PlatformDigestServiceImpl(schemeMetadata),
    private val keyPairGeneratorFactory: KeyPairGeneratorFactory = JavaKeyPairGenerator()
) : CryptoService {
    private var wrapCounter = AtomicInteger()
    private var unwrapCounter = AtomicInteger()

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val signatureInstances = SignatureInstances(schemeMetadata.providers)

    override val supportedSchemes = deriveSupportedSchemes(schemeMetadata)

    override val extensions = listOf(CryptoServiceExtensions.REQUIRE_WRAPPING_KEY)

    override fun createWrappingKey(masterKeyAlias: String, failIfExists: Boolean, context: Map<String, String>) {
        require(masterKeyAlias != "") { "Alias must not be empty" }
        val isCached = wrappingKeyCache?.getIfPresent(masterKeyAlias) != null
        val isAvailable = if (isCached) true else wrappingKeyStore.findWrappingKey(masterKeyAlias) != null
        logger.trace {
            "createWrappingKey(alias=$masterKeyAlias failIfExists=$failIfExists) cached=$isCached available=$isAvailable"
        }
        if (isAvailable) {
            if (failIfExists) throw IllegalStateException("There is an existing key with the alias: $masterKeyAlias")
            logger.debug { "Not creating wrapping key for '$masterKeyAlias' since a key is available" }
            return
        }
        val wrappingKey = WrappingKeyImpl.generateWrappingKey(schemeMetadata)
        val wrappingKeyEncrypted = rootWrappingKey.wrap(wrappingKey)
        val wrappingKeyInfo =
            WrappingKeyInfo(WRAPPING_KEY_ENCODING_VERSION, wrappingKey.algorithm, wrappingKeyEncrypted)
        wrappingKeyStore.saveWrappingKey(masterKeyAlias, wrappingKeyInfo)
        wrappingKeyCache?.put(masterKeyAlias, wrappingKey)
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

    override fun generateKeyPair(spec: KeyGenerationSpec, context: Map<String, String>): GeneratedWrappedKey {
        require(supportedSchemes.containsKey(spec.keyScheme)) {
            "Unsupported key scheme: ${spec.keyScheme.codeName}"
        }
        logger.trace {
            "generateKeyPair(alias=${spec.alias},masterKeyAlias={$spec.masterKeyAlias},scheme={spec.keyScheme.codeName})"
        }
        val keyPairGenerator = keyPairGeneratorFactory.getInstance(
            spec.keyScheme.algorithmName,
            providerFor(spec.keyScheme)
        )
        val keySize = spec.keyScheme.keySize
        if (spec.keyScheme.algSpec != null) {
            keyPairGenerator.initialize(spec.keyScheme.algSpec, schemeMetadata.secureRandom)
        } else if (keySize != null) {
            keyPairGenerator.initialize(keySize, schemeMetadata.secureRandom)
        }
        val keyPair = keyPairGenerator.generateKeyPair()
        val wrappingKey = getWrappingKey(spec.masterKeyAlias)
        wrapCounter.incrementAndGet()
        val keyMaterial = wrappingKey.wrap(keyPair.private)
        privateKeyCache?.put(keyPair.public, keyPair.private)
        return GeneratedWrappedKey(
            publicKey = keyPair.public,
            keyMaterial = keyMaterial,
            encodingVersion = PRIVATE_KEY_ENCODING_VERSION
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

    @VisibleForTesting
    fun getWrappingKey(alias: String): WrappingKey =
        wrappingKeyCache?.get(alias, ::getWrappingKeyUncached) ?: getWrappingKeyUncached(alias)

    private fun getWrappingKeyUncached(alias: String): WrappingKey {
        // use IllegalArgumentException instead for not found?
        val wrappingKeyInfo =
            wrappingKeyStore.findWrappingKey(alias)
                ?: throw IllegalStateException("Wrapping key with alias $alias not found")
        require(wrappingKeyInfo.encodingVersion == WRAPPING_KEY_ENCODING_VERSION) {
            "Unknown wrapping key encoding. Expected to be $WRAPPING_KEY_ENCODING_VERSION"
        }
        // TODO remove this restriction? Different levels of wrapping key could sensibly use different algorithms
        require(rootWrappingKey.algorithm == wrappingKeyInfo.algorithmName) {
            "Expected algorithm is ${rootWrappingKey.algorithm} but was ${wrappingKeyInfo.algorithmName}"
        }
        return rootWrappingKey.unwrapWrappingKey(wrappingKeyInfo.keyMaterial)
    }

    @VisibleForTesting
    fun getPrivateKey(publicKey: PublicKey, spec: KeyMaterialSpec): PrivateKey =
        privateKeyCache?.get(publicKey) { getPrivateKeyUncached(spec) } ?: getPrivateKeyUncached(spec)

    private fun getPrivateKeyUncached(spec: KeyMaterialSpec): PrivateKey {
        unwrapCounter.incrementAndGet()
        return getWrappingKey(spec.masterKeyAlias).unwrap(spec.keyMaterial)
    }

    @VisibleForTesting
    fun wrappingKeyExists(wrappingKeyAlias: String): Boolean =
        wrappingKeyCache?.getIfPresent(wrappingKeyAlias) != null || wrappingKeyStore.findWrappingKey(wrappingKeyAlias) != null

    fun getWrapCounter() = wrapCounter.get()
    fun getUnwrapCounter() = unwrapCounter.get()
}
