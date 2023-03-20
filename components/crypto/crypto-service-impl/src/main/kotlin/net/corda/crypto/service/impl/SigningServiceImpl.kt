package net.corda.crypto.service.impl

import net.corda.crypto.cipher.suite.CRYPTO_CATEGORY
import net.corda.crypto.cipher.suite.CRYPTO_TENANT_ID
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.cipher.suite.KeyMaterialSpec
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.cipher.suite.SharedSecretAliasSpec
import net.corda.crypto.cipher.suite.SharedSecretWrappedSpec
import net.corda.crypto.cipher.suite.SigningAliasSpec
import net.corda.crypto.cipher.suite.SigningWrappedSpec
import net.corda.crypto.cipher.suite.publicKeyId
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.core.KEY_LOOKUP_INPUT_ITEMS_LIMIT
import net.corda.crypto.core.KeyAlreadyExistsException
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningKeyStore
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.KeyOrderBy
import net.corda.crypto.service.SigningKeyInfo
import net.corda.crypto.service.SigningService
import net.corda.utilities.debug
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException
import java.security.PublicKey

@Suppress("TooManyFunctions")
class SigningServiceImpl(
    private val store: SigningKeyStore,
    private val cryptoServiceFactory: CryptoServiceFactory,
    override val schemeMetadata: CipherSchemeMetadata,
    private val digestService: PlatformDigestService
) : SigningService {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    data class OwnedKeyRecord(val publicKey: PublicKey, val data: SigningCachedKey)

    override fun getSupportedSchemes(tenantId: String, category: String): List<String> {
        logger.debug { "getSupportedSchemes(tenant=$tenantId, category=$category)" }
        val ref = cryptoServiceFactory.findInstance(tenantId = tenantId, category = category)
        return ref.instance.supportedSchemes.map { it.key.codeName }
    }

    override fun lookup(
        tenantId: String,
        skip: Int,
        take: Int,
        orderBy: KeyOrderBy,
        filter: Map<String, String>
    ): Collection<SigningKeyInfo> {
        logger.debug {
            "lookup(tenantId=$tenantId, skip=$skip, take=$take, orderBy=$orderBy, filter=[${filter.keys.joinToString()}]"
        }
        return store.lookup(
            tenantId,
            skip,
            take,
            orderBy.toSigningKeyOrderBy(),
            filter
        ).map { key -> key.toSigningKeyInfo() }
    }

    override fun lookupByIds(tenantId: String, keyIds: List<ShortHash>): Collection<SigningKeyInfo> =
        store.lookupByIds(tenantId, keyIds)
            .map { foundKey ->
                foundKey.toSigningKeyInfo()
            }

    override fun lookupByFullIds(tenantId: String, fullKeyIds: List<SecureHash>): Collection<SigningKeyInfo> =
        store.lookupByFullIds(tenantId, fullKeyIds)
            .map { foundKey ->
                foundKey.toSigningKeyInfo()
            }

    override fun createWrappingKey(
        hsmId: String,
        failIfExists: Boolean,
        masterKeyAlias: String,
        context: Map<String, String>
    ) {
        logger.debug {
            "createWrappingKey(hsmId=$hsmId,masterKeyAlias=$masterKeyAlias,failIfExists=$failIfExists," +
                    "onBehalf=${context[CRYPTO_TENANT_ID]})"
        }
        cryptoServiceFactory.getInstance(hsmId).createWrappingKey(masterKeyAlias, failIfExists, context)
    }

    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        scheme: KeyScheme,
        context: Map<String, String>
    ): PublicKey =
        doGenerateKeyPair(
            tenantId = tenantId,
            category = category,
            alias = alias,
            externalId = null,
            scheme = scheme,
            context = context
        )

    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        externalId: String,
        scheme: KeyScheme,
        context: Map<String, String>
    ): PublicKey =
        doGenerateKeyPair(
            tenantId = tenantId,
            category = category,
            alias = alias,
            externalId = externalId,
            scheme = scheme,
            context = context
        )

    override fun freshKey(
        tenantId: String,
        category: String,
        scheme: KeyScheme,
        context: Map<String, String>
    ): PublicKey =
        doGenerateKeyPair(
            tenantId = tenantId,
            category = category,
            alias = null,
            externalId = null,
            scheme = scheme,
            context = context
        )

    override fun freshKey(
        tenantId: String,
        category: String,
        externalId: String,
        scheme: KeyScheme,
        context: Map<String, String>
    ): PublicKey =
        doGenerateKeyPair(
            tenantId = tenantId,
            category = category,
            alias = null,
            externalId = externalId,
            scheme = scheme,
            context = context
        )

    override fun sign(
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey {
        val record = getOwnedKeyRecord(tenantId, publicKey)
        logger.debug { "sign(tenant=$tenantId, publicKey=${record.data.id})" }
        val scheme = schemeMetadata.findKeyScheme(record.data.schemeCodeName)
        val cryptoService = cryptoServiceFactory.getInstance(record.data.hsmId)
        val spec = if (record.data.keyMaterial != null)
            SigningWrappedSpec(getKeySpec(record, publicKey, tenantId), record.publicKey, scheme, signatureSpec)
        else
            SigningAliasSpec(getHsmAlias(record, publicKey, tenantId), publicKey, scheme, signatureSpec)
        val signedBytes = cryptoService.sign(spec, data, context + mapOf(CRYPTO_TENANT_ID to tenantId))
        return DigitalSignature.WithKey(record.publicKey, signedBytes)
    }
    
    override fun deriveSharedSecret(
        tenantId: String,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        context: Map<String, String>
    ): ByteArray {
        val record = getOwnedKeyRecord(tenantId, publicKey)
        logger.info(
            "deriveSharedSecret(tenant={}, publicKey={}, otherPublicKey={})",
            tenantId,
            record.data.id,
            otherPublicKey.publicKeyId()
        )
        val scheme = schemeMetadata.findKeyScheme(record.data.schemeCodeName)
        val cryptoService = cryptoServiceFactory.getInstance(record.data.hsmId)
        val spec = if (record.data.keyMaterial != null)
            SharedSecretWrappedSpec(getKeySpec(record, publicKey, tenantId), record.publicKey, scheme, otherPublicKey)
        else
            SharedSecretAliasSpec(getHsmAlias(record, publicKey, tenantId), record.publicKey, scheme, otherPublicKey)
        return cryptoService.deriveSharedSecret(spec, context + mapOf(CRYPTO_TENANT_ID to tenantId))
    }

    private fun getHsmAlias(
        record: OwnedKeyRecord,
        publicKey: PublicKey,
        tenantId: String
    ): String = record.data.hsmAlias ?: throw IllegalStateException(
        "HSM alias must be specified if key material is not specified, and both are null for ${publicKey.publicKeyId()} of tenant $tenantId"
    )

    @Suppress("LongParameterList")
    private fun doGenerateKeyPair(
        tenantId: String,
        category: String,
        alias: String?,
        externalId: String?,
        scheme: KeyScheme,
        context: Map<String, String>
    ): PublicKey {
        logger.info("generateKeyPair(tenant={}, category={}, alias={}))", tenantId, category, alias)
        val ref = cryptoServiceFactory.findInstance(tenantId = tenantId, category = category)
        if (alias != null && store.find(tenantId, alias) != null) {
            throw KeyAlreadyExistsException(
                "The key with alias $alias already exists for tenant $tenantId",
                alias,
                tenantId
            )
        }
        require(ref.masterKeyAlias != null) { "The master key alias must be defined for tenant $tenantId category $category" }
        val generatedKey = ref.instance.generateKeyPair(
            KeyGenerationSpec(scheme, alias, ref.masterKeyAlias),
            context + mapOf(
                CRYPTO_TENANT_ID to tenantId,
                CRYPTO_CATEGORY to category
            )
        )
        store.save(tenantId, ref.toSaveKeyContext(generatedKey, alias, scheme, externalId))
        return schemeMetadata.toSupportedPublicKey(generatedKey.publicKey)
    }

    @Suppress("NestedBlockDepth")
    private fun getOwnedKeyRecord(tenantId: String, publicKey: PublicKey): OwnedKeyRecord {
        if (publicKey is CompositeKey) {
            val leafKeysIdsChunks = publicKey.leafKeys.map {
                it.fullIdHash(schemeMetadata, digestService) to it
            }.chunked(KEY_LOOKUP_INPUT_ITEMS_LIMIT)
            for (chunk in leafKeysIdsChunks) {
                val found = store.lookupByFullIds(
                    tenantId,
                    chunk.map { it.first }
                )
                if (found.isNotEmpty()) {
                    for (key in chunk) {
                        val first = found.firstOrNull { it.fullId == key.first }
                        if (first != null) {
                            return OwnedKeyRecord(key.second, first)
                        }
                    }
                }
            }
            throw IllegalArgumentException(
                "The tenant $tenantId doesn't own any public key in '${publicKey.publicKeyId()}' composite key."
            )
        } else {
            return store.find(tenantId, publicKey)?.let { OwnedKeyRecord(publicKey, it) }
                ?: throw IllegalArgumentException(
                    "The tenant $tenantId doesn't own public key '${publicKey.publicKeyId()}'."
                )
        }
    }

    @Suppress("ThrowsCount")
    private fun getKeySpec(
        record: OwnedKeyRecord,
        publicKey: PublicKey,
        tenantId: String
    ): KeyMaterialSpec {
        val keyMaterial: ByteArray = record.data.keyMaterial ?: throw IllegalStateException(
            "The key material is null for public key ${publicKey.publicKeyId()} of tenant $tenantId  "
        )
        val masterKeyAlias = record.data.masterKeyAlias ?: throw IllegalStateException(
            "The master key alias for public key ${publicKey.publicKeyId()} of tenant $tenantId must be specified, but is null"
        )
        val encodingVersion = record.data.encodingVersion ?: throw IllegalStateException(
            "The encoding version for public key ${publicKey.publicKeyId()} of tenant $tenantId must be specified, but is null"
        )
        return KeyMaterialSpec(
            keyMaterial = keyMaterial,
            wrappingKeyAlias = masterKeyAlias,
            encodingVersion = encodingVersion
        )
    }


    private fun KeyOrderBy.toSigningKeyOrderBy(): SigningKeyOrderBy =
        SigningKeyOrderBy.valueOf(name)

    private fun SigningCachedKey.toSigningKeyInfo(): SigningKeyInfo =
        SigningKeyInfo(
            id = id,
            tenantId = tenantId,
            category = category,
            alias = alias,
            hsmAlias = hsmAlias,
            publicKey = publicKey,
            schemeCodeName = schemeCodeName,
            masterKeyAlias = masterKeyAlias,
            externalId = externalId,
            encodingVersion = encodingVersion,
            created = timestamp
        )
}
