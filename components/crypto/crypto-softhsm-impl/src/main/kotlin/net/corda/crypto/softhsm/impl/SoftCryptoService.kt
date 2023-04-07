package net.corda.crypto.softhsm.impl

import com.github.benmanes.caffeine.cache.Cache
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
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.core.aes.WrappingKeyImpl
import net.corda.crypto.hes.core.impl.deriveDHSharedSecret
import net.corda.crypto.impl.SignatureInstances
import net.corda.crypto.impl.getSigningData
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.WrappingRepositoryFactory
import net.corda.crypto.softhsm.deriveSupportedSchemes
import net.corda.utilities.debug
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import javax.crypto.Cipher

const val WRAPPING_KEY_ENCODING_VERSION: Int = 1
const val PRIVATE_KEY_ENCODING_VERSION: Int = 1

/**
 * This class is all about the business logic of generating, storing and using key pairs; it can be run
 * without a database, without OSGi and without SmartConfig, which makes it easy to test.
 *
 * @param wrappingRepositoryFactory which provides a factory for [WrappingRepository], which provides save and
 *        find operations for wrapping keys on a specific tenant.
 * @param schemeMetadata which specifies encryption schemes, digests schemes and a source of randomness
 * @param defaultUnmanagedWrappingKeyName The unmanaged wrapping key that will be used by default for new wrapping keys
 * @param digestService supply a platform digest service instance; if not one will be constructed
 * @param wrappingKeyCache an optional [Cache] which optimises access to wrapping keys, or null for no caching
 * @param privateKeyCache an optional [Cache] which optimises access to private keys, or null for no caching
 * @param keyPairGeneratorFactory creates a key pair generator given algorithm and provider. For instance:
 *     `{ algorithm: String, provider: Provider -> KeyPairGenerator.getInstance(algorithm, provider) }`
 * @param wrappingKeyFactory creates a wrapping key given scheme metadata. For instance:
 *        `{ WrappingKeyImpl.generateWrappingKey(it) }`
 */


@Suppress("LongParameterList")
class SoftCryptoService(
    private val wrappingRepositoryFactory: WrappingRepositoryFactory,
    private val schemeMetadata: CipherSchemeMetadata,
    private val defaultUnmanagedWrappingKeyName: String,
    private val unmanagedWrappingKeys: Map<String, WrappingKey>,
    private val digestService: PlatformDigestService,
    private val wrappingKeyCache: Cache<String, WrappingKey>?,
    private val privateKeyCache: Cache<PublicKey, PrivateKey>?,
    private val keyPairGeneratorFactory: (algorithm: String, provider: Provider) -> KeyPairGenerator,
    private val wrappingKeyFactory: (schemeMetadata: CipherSchemeMetadata) -> WrappingKey = {
        WrappingKeyImpl.generateWrappingKey(it)
    },
) : Closeable, CryptoService {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val signatureInstances = SignatureInstances(schemeMetadata.providers)

    override val supportedSchemes = deriveSupportedSchemes(schemeMetadata)

    override val extensions = listOf(CryptoServiceExtensions.REQUIRE_WRAPPING_KEY)

    override fun createWrappingKey(wrappingKeyAlias: String, failIfExists: Boolean, context: Map<String, String>) {
        require(wrappingKeyAlias != "") { "Alias must not be empty" }
        val isCached = wrappingKeyCache?.getIfPresent(wrappingKeyAlias) != null
        val tenantId = computeTenantId(context)
        val isAvailable = if (isCached) true else wrappingRepositoryFactory.create(tenantId).use {
            it.findKey(wrappingKeyAlias) != null
        }
        logger.trace {
            "createWrappingKey(alias=$wrappingKeyAlias failIfExists=$failIfExists) cached=$isCached available=$isAvailable"
        }
        if (isAvailable) {
            if (failIfExists) throw IllegalStateException("There is an existing key with the alias: $wrappingKeyAlias")
            logger.debug { "Not creating wrapping key for '$wrappingKeyAlias' since a key is available" }
            return
        }
        val wrappingKey = wrappingKeyFactory(schemeMetadata)
        val parentKeyName = context.get("wrappingKey") ?: defaultUnmanagedWrappingKeyName
        val parentKey = unmanagedWrappingKeys.get(parentKeyName)
        if (parentKey == null) {
            throw IllegalStateException("No wrapping key $parentKeyName found")
        }
        val wrappingKeyEncrypted = parentKey.wrap(wrappingKey)
        val wrappingKeyInfo =
            WrappingKeyInfo(
                WRAPPING_KEY_ENCODING_VERSION,
                wrappingKey.algorithm,
                wrappingKeyEncrypted,
                1,
                parentKeyName
            )
        wrappingRepositoryFactory.create(tenantId).use {
            it.saveKey(wrappingKeyAlias, wrappingKeyInfo)
        }
        logger.trace("Stored wrapping key alias $wrappingKeyAlias context ${context.toString()}")
        wrappingKeyCache?.put(wrappingKeyAlias, wrappingKey)
    }

    override fun delete(alias: String, context: Map<String, String>): Boolean =
        throw UnsupportedOperationException("The service does not support key deletion.")

    override fun deriveSharedSecret(
        spec: SharedSecretSpec,
        context: Map<String, String>,
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
            obtainAndStorePrivateKey(spec.publicKey, spec.keyMaterialSpec, computeTenantId(context)),
            spec.otherPublicKey
        )
    }

    override fun generateKeyPair(spec: KeyGenerationSpec, context: Map<String, String>): GeneratedWrappedKey {
        val tenantId = computeTenantId(context)
        require(supportedSchemes.containsKey(spec.keyScheme)) {
            "Unsupported key scheme: ${spec.keyScheme.codeName}"
        }
        logger.trace {
            "generateKeyPair(alias=${spec.alias},masterKeyAlias={$spec.masterKeyAlias},scheme={spec.keyScheme.codeName})"
        }
        val keyPairGenerator = keyPairGeneratorFactory(
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
        val wrappingKey = obtainAndStoreWrappingKey(spec.wrappingKeyAlias, tenantId)
        val keyMaterial = wrappingKey.wrap(keyPair.private)
        privateKeyCache?.put(keyPair.public, keyPair.private)
        return GeneratedWrappedKey(
            publicKey = keyPair.public,
            keyMaterial = keyMaterial,
            encodingVersion = PRIVATE_KEY_ENCODING_VERSION
        )
    }

    private fun computeTenantId(context: Map<String, String>) =
        context.get("tennntId") ?: CryptoTenants.CRYPTO

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
        return sign(
            spec,
            obtainAndStorePrivateKey(spec.publicKey, spec.keyMaterialSpec, computeTenantId(context)),
            data
        )
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

    private fun obtainAndStoreWrappingKey(alias: String, tenantId: String): WrappingKey =
        wrappingKeyCache?.getIfPresent(alias) ?: run {
            // use IllegalArgumentException instead for not found?
            val wrappingKeyInfo = wrappingRepositoryFactory.create(tenantId).use { it.findKey(alias) }
                ?: throw IllegalStateException("Wrapping key with alias $alias not found")
            require(wrappingKeyInfo.encodingVersion == WRAPPING_KEY_ENCODING_VERSION) {
                "Unknown wrapping key encoding. Expected to be $WRAPPING_KEY_ENCODING_VERSION"
            }
            val parentKey = unmanagedWrappingKeys.get(wrappingKeyInfo.parentKeyAlias)
            if (parentKey == null) {
                throw IllegalStateException("Unknown parent key ${wrappingKeyInfo.parentKeyAlias} for $alias")
            }
            // TODO remove this restriction? Different levels of wrapping key could sensibly use different algorithms
            require(parentKey.algorithm == wrappingKeyInfo.algorithmName) {
                "Expected algorithm is ${parentKey.algorithm} but was ${wrappingKeyInfo.algorithmName}"
            }

            return parentKey.unwrapWrappingKey(wrappingKeyInfo.keyMaterial).also {
                wrappingKeyCache?.put(alias, it)
            }
        }

    private fun obtainAndStorePrivateKey(publicKey: PublicKey, spec: KeyMaterialSpec, tenantId: String): PrivateKey =
        privateKeyCache?.getIfPresent(publicKey) ?: obtainAndStoreWrappingKey(spec.wrappingKeyAlias, tenantId).unwrap(
            spec.keyMaterial
        ).also {
            privateKeyCache?.put(publicKey, it)
        }
    

    override fun close() {
    }
}
