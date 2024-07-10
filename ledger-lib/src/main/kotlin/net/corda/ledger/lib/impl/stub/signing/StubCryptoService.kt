package net.corda.ledger.lib.impl.stub.signing

import com.github.benmanes.caffeine.cache.Cache
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.cipher.suite.CRYPTO_TENANT_ID
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.CryptoServiceExtensions
import net.corda.crypto.cipher.suite.CustomSignatureSpec
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.cipher.suite.KeyMaterialSpec
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.cipher.suite.SharedSecretSpec
import net.corda.crypto.cipher.suite.SigningWrappedSpec
import net.corda.crypto.cipher.suite.getParamsSafely
import net.corda.crypto.cipher.suite.publicKeyId
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.cipher.suite.schemes.KeySchemeCapability
import net.corda.crypto.core.ClusterCryptoDb
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.core.KeyOrderBy
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.SigningKeyInfo
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.core.isRecoverable
import net.corda.crypto.impl.SignatureInstances
import net.corda.crypto.impl.getSigningData
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.softhsm.SigningRepositoryFactory
import net.corda.crypto.softhsm.WrappingRepositoryFactory
import net.corda.crypto.softhsm.deriveSupportedSchemes
import net.corda.crypto.softhsm.impl.OwnedKeyRecord
import net.corda.crypto.softhsm.impl.ShortHashCacheKey
import net.corda.crypto.softhsm.impl.WRAPPING_KEY_ENCODING_VERSION
import net.corda.metrics.CordaMetrics
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoException
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.util.*
import javax.crypto.Cipher

class StubCryptoService(
    private val digestService: PlatformDigestService,
    private val privateKeyCache: Cache<PublicKey, PrivateKey>?,
    private val wrappingKeyCache: Cache<String, WrappingKey>?,
    private val shortHashCache: Cache<ShortHashCacheKey, SigningKeyInfo>,
    private val wrappingRepositoryFactory: WrappingRepositoryFactory,
    private val unmanagedWrappingKeys: Map<String, WrappingKey>,
    private val signingRepositoryFactory: SigningRepositoryFactory,
    override val schemeMetadata: CipherSchemeMetadata
) : CryptoService {

    private val signatureInstances = SignatureInstances(schemeMetadata.providers)

    override fun sign(
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignatureWithKey {
        val record = getOwnedKeyRecord(tenantId, publicKey)
        val scheme = schemeMetadata.findKeyScheme(record.data.schemeCodeName)
        val spec =
            SigningWrappedSpec(
                getKeySpec(record, publicKey, tenantId),
                record.publicKey,
                scheme,
                signatureSpec,
                record.data.category
            )
        val signedBytes = sign(spec, data, context + mapOf(CRYPTO_TENANT_ID to tenantId))
        return DigitalSignatureWithKey(record.publicKey, signedBytes)
    }

    private fun getKeySpec(
        record: OwnedKeyRecord,
        publicKey: PublicKey,
        tenantId: String,
    ): KeyMaterialSpec {
        val keyMaterial: ByteArray = record.data.keyMaterial
        val masterKeyAlias = record.data.wrappingKeyAlias
        val encodingVersion = record.data.encodingVersion ?: throw IllegalStateException(
            "The encoding version for public key ${publicKey.publicKeyId()} of tenant $tenantId must be specified, but is null"
        )
        return KeyMaterialSpec(
            keyMaterial = keyMaterial,
            wrappingKeyAlias = masterKeyAlias,
            encodingVersion = encodingVersion
        )
    }

    private fun getOwnedKeyRecord(tenantId: String, publicKey: PublicKey): OwnedKeyRecord {
        if (publicKey is CompositeKey) {
            val found = lookupSigningKeysByPublicKeyHashes(tenantId, publicKey.leafKeys.map { it.fullIdHash() })
            val hit = found.firstOrNull()
            if (hit != null) return OwnedKeyRecord(publicKey.leafKeys.filter { it.fullIdHash() == hit.fullId }
                .first(), hit)
            throw IllegalArgumentException(
                "The tenant $tenantId doesn't own any public key in '${publicKey.publicKeyId()}' composite key."
            )
        }
        // now we are not dealing with composite keys
        val requestedFullKeyId = publicKey.fullIdHash(schemeMetadata, digestService)
        val keyId = ShortHash.of(requestedFullKeyId)
        val cacheKey = ShortHashCacheKey(tenantId, keyId)
        val signingKeyInfo = shortHashCache.getIfPresent(cacheKey) ?: run {
            signingRepositoryFactory.getInstance(tenantId).use { repo ->
                repo.findKey(publicKey)
            } ?: throw IllegalArgumentException("The public key '${publicKey.publicKeyId()}' was not found")
        }

        return OwnedKeyRecord(publicKey, signingKeyInfo)
    }

    override fun sign(spec: SigningWrappedSpec, data: ByteArray, context: Map<String, String>): ByteArray {
        val category = context["category"]
        require(category.isNullOrEmpty() || CryptoConsts.Categories.all.contains(category)) {
            "Category value $category is not recognised"
        }
        require(category.isNullOrEmpty() || spec.category.equals(category)) {
            "Provided category $category does not match the key's category ${spec.category}"
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
        val privateKey = obtainAndStorePrivateKey(spec.publicKey, spec.keyMaterialSpec, computeTenantId(context))
        val signingData = spec.signatureSpec.getSigningData(digestService, data)
        val signatureBytes = recoverable("sign") {
            if (spec.signatureSpec is CustomSignatureSpec && spec.keyScheme.algorithmName == "RSA") {
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
        }
        return signatureBytes
    }

    private fun providerFor(scheme: KeyScheme): Provider =
        schemeMetadata.providers.getValue(scheme.providerName)

    private fun obtainAndStorePrivateKey(
        publicKey: PublicKey,
        spec: KeyMaterialSpec,
        tenantId: String
    ): PrivateKey =
        privateKeyCache?.getIfPresent(publicKey) ?: obtainAndStoreWrappingKey(
            spec.wrappingKeyAlias,
            tenantId
        ).unwrap(
            spec.keyMaterial
        ).also {
            privateKeyCache?.put(publicKey, it)
        }

    private fun obtainAndStoreWrappingKey(alias: String, tenantId: String): WrappingKey =
        recoverable("obtainAndStoreWrappingKey") {
            CordaMetrics.Metric.Crypto.WrappingKeyCreationTimer.builder()
                .withTag(CordaMetrics.Tag.Tenant, tenantId)
                .build()
                .recordCallable {
                    checkNotNull(getWrappingKeyFromRepositoryOrCache(alias, tenantId, true))
                }
        }

    private fun getWrappingKeyFromRepositoryOrCache(
        alias: String,
        tenantId: String,
        throwOnFailure: Boolean
    ) = wrappingKeyCache?.getIfPresent(alias) ?: run {
        getWrappingKeyFromRepository(tenantId, alias, throwOnFailure)
    }

    private fun getWrappingKeyFromRepository(
        tenantId: String,
        alias: String,
        throwOnFailure: Boolean
    ) = wrappingRepositoryFactory.create(tenantId)
        .use {
            recoverable("getWrappingKeyFromRepository findKey") {
                it.findKey(alias)
            }
        }?.let { wrappingKeyInfo ->
            check(wrappingKeyInfo.encodingVersion == WRAPPING_KEY_ENCODING_VERSION) {
                "Unknown wrapping key encoding. Expected to be $WRAPPING_KEY_ENCODING_VERSION"
            }
            unmanagedWrappingKeys.get(wrappingKeyInfo.parentKeyAlias)?.let { parentKey ->
                // TODO remove this restriction? Different levels of wrapping key could sensibly use different algorithms
                check(parentKey.algorithm == wrappingKeyInfo.algorithmName) {
                    "Expected algorithm is ${parentKey.algorithm} but was ${wrappingKeyInfo.algorithmName}"
                }
                try {
                    parentKey.unwrapWrappingKey(wrappingKeyInfo.keyMaterial).also {
                        wrappingKeyCache?.put(alias, it)
                    }
                } catch (ex: Exception) {
                    throw IllegalStateException(
                        "Could not decrypt wrapping key with alias: ${wrappingKeyInfo.alias} with parent key: " +
                                "${wrappingKeyInfo.parentKeyAlias} because ${ex.message}."
                    )
                }
            } ?: run {
                val msg = "Unknown parent key ${wrappingKeyInfo.parentKeyAlias} for $alias"
                if (throwOnFailure) {
                    throw IllegalStateException(msg)
                } else {
                    null
                }
            }
        } ?: run {
        val msg = "Wrapping key with alias $alias not found"
        if (throwOnFailure) {
            throw IllegalStateException(msg)
        } else {
            null
        }
    }

    private fun <R> recoverable(description: String, block: () -> R) = try {
        block()
    } catch (e: RuntimeException) {
        if (!e.isRecoverable())
            throw e
        else
            throw CryptoException("Calling $description failed in a potentially recoverable way", e)
    }

    private fun computeTenantId(context: Map<String, String>) = context.get("tenantId") ?: ClusterCryptoDb.SCHEMA_NAME

    override fun querySigningKeys(
        tenantId: String,
        skip: Int,
        take: Int,
        orderBy: KeyOrderBy,
        filter: Map<String, String>
    ): Collection<SigningKeyInfo> {
        return signingRepositoryFactory.getInstance(tenantId).use {
            it.query(
                skip,
                take,
                SigningKeyOrderBy.valueOf(orderBy.name),
                filter
            )
        }
    }

    override fun lookupSigningKeysByPublicKeyShortHash(
        tenantId: String,
        keyIds: List<ShortHash>
    ): Collection<SigningKeyInfo> {
        val cachedKeys =
            shortHashCache.getAllPresent(keyIds.mapTo(mutableSetOf()) {
                ShortHashCacheKey(tenantId, it)
            }).mapTo(mutableSetOf()) { it.value }
        val notFound: List<ShortHash> = keyIds - cachedKeys.map { it.id }.toSet()
        if (notFound.isEmpty()) return cachedKeys
        val fetchedKeys = signingRepositoryFactory.getInstance(tenantId).use {
            it.lookupByPublicKeyShortHashes(notFound.toMutableSet())
        }
        fetchedKeys.forEach { shortHashCache.put(ShortHashCacheKey(tenantId, it.id), it) }

        return cachedKeys + fetchedKeys
    }

    override fun lookupSigningKeysByPublicKeyHashes(
        tenantId: String,
        fullKeyIds: List<SecureHash>
    ): Collection<SigningKeyInfo> {
        fun filterOutMismatches(found: Collection<SigningKeyInfo>) =
            found.map { foundSigningKeyInfo ->
                if (fullKeyIds.contains(foundSigningKeyInfo.fullId)) {
                    foundSigningKeyInfo
                } else {
                    null
                }
            }.filterNotNull()

        val keyIds = fullKeyIds.map { ShortHash.of(it) }
        val cachedMap = shortHashCache.getAllPresent(keyIds.mapTo(mutableSetOf()) { ShortHashCacheKey(tenantId, it) })
        val cachedKeys = cachedMap.map { it.value }
        val cachedMatchingKeys = filterOutMismatches(cachedKeys)
        if (cachedMatchingKeys.size == fullKeyIds.size) return cachedMatchingKeys
        val notFound = fullKeyIds.filter { hash -> cachedMatchingKeys.count { it.fullId == hash } == 0 }
        // if fullKeyIds has duplicates it's possible that notFound is empty here, even though
        // we didn't get as many records from the cache as the number of keys we expected
        if (notFound.isEmpty()) return cachedMatchingKeys

        val fetchedKeys = signingRepositoryFactory.getInstance(tenantId).use {
            it.lookupByPublicKeyHashes(notFound.toMutableSet())
        }
        val fetchedMatchingKeys = filterOutMismatches(fetchedKeys)
        fetchedMatchingKeys.forEach {
            shortHashCache.put(ShortHashCacheKey(tenantId, it.id), it)
        }
        return cachedMatchingKeys + fetchedMatchingKeys
    }


    /**
     * -----------------------------------------------------------------------------------------------------------------
     * ++++++++++++++++++++++++++++++++++++ WE DON'T NEED THESE :) +++++++++++++++++++++++++++++++++++++++++++++++++++++
     * -----------------------------------------------------------------------------------------------------------------
     */
    override val extensions: List<CryptoServiceExtensions>
        get() = TODO("Not yet implemented")
    override val supportedSchemes: Map<KeyScheme, List<SignatureSpec>> = deriveSupportedSchemes(CipherSchemeMetadataImpl())

    override fun generateKeyPair(spec: KeyGenerationSpec, context: Map<String, String>): GeneratedWrappedKey {
        TODO("Not yet implemented")
    }

    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String?,
        externalId: String?,
        scheme: KeyScheme,
        context: Map<String, String>
    ): GeneratedWrappedKey {
        TODO("Not yet implemented")
    }

    override fun createWrappingKey(wrappingKeyAlias: String, failIfExists: Boolean, context: Map<String, String>) {
        TODO("Not yet implemented")
    }

    override fun createWrappingKey(
        hsmId: String,
        failIfExists: Boolean,
        masterKeyAlias: String,
        context: Map<String, String>
    ) {
        TODO("Not yet implemented")
    }

    override fun delete(alias: String, context: Map<String, String>): Boolean {
        TODO("Not yet implemented")
    }

    override fun deriveSharedSecret(spec: SharedSecretSpec, context: Map<String, String>): ByteArray {
        TODO("Not yet implemented")
    }

    override fun deriveSharedSecret(
        tenantId: String,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        context: Map<String, String>
    ): ByteArray {
        TODO("Not yet implemented")
    }

    override fun rewrapWrappingKey(tenantId: String, targetAlias: String, newParentKeyAlias: String): Int {
        TODO("Not yet implemented")
    }

    override fun encrypt(tenantId: String, plainBytes: ByteArray, alias: String?): ByteArray {
        TODO("Not yet implemented")
    }

    override fun decrypt(tenantId: String, cipherBytes: ByteArray, alias: String?): ByteArray {
        TODO("Not yet implemented")
    }

    override fun rewrapAllSigningKeysWrappedBy(managedWrappingKeyId: UUID, tenantId: String): Int {
        TODO("Not yet implemented")
    }
}