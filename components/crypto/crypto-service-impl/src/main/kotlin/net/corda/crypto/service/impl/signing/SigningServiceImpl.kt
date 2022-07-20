package net.corda.crypto.service.impl.signing

import net.corda.crypto.persistence.signing.SigningCachedKey
import net.corda.crypto.persistence.signing.SigningKeyOrderBy
import net.corda.crypto.persistence.signing.SigningKeyStore
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.KeyOrderBy
import net.corda.crypto.service.SigningKeyInfo
import net.corda.crypto.service.SigningService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.cipher.suite.CRYPTO_TENANT_ID
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.KEY_LOOKUP_INPUT_ITEMS_LIMIT
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.publicKeyId
import java.security.PublicKey

@Suppress("TooManyFunctions")
class SigningServiceImpl(
    private val store: SigningKeyStore,
    private val cryptoServiceFactory: CryptoServiceFactory,
    override val schemeMetadata: CipherSchemeMetadata
) : SigningService {
    companion object {
        private val logger = contextLogger()
    }

    override fun getSupportedSchemes(tenantId: String, category: String): List<String> {
        logger.debug { "getSupportedSchemes(tenant=$tenantId, category=$category)" }
        return cryptoServiceFactory.getServiceRef(tenantId = tenantId, category = category).getSupportedSchemes()
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

    override fun lookup(tenantId: String, ids: List<String>): Collection<SigningKeyInfo> {
        logger.debug { "lookup(tenantId=$tenantId, ids=[${ids.joinToString()}])" }
        return store.lookup(tenantId, ids).map { key -> key.toSigningKeyInfo() }
    }

    override fun createWrappingKey(
        configId: String,
        failIfExists: Boolean,
        masterKeyAlias: String,
        context: Map<String, String>
    ) {
        logger.debug {
            "createWrappingKey(configId=$configId,masterKeyAlias=$masterKeyAlias,failIfExists=$failIfExists," +
                    "onBehalf=${context[CRYPTO_TENANT_ID]})"
        }
        cryptoServiceFactory.getInstance(configId).createWrappingKey(
            masterKeyAlias = masterKeyAlias,
            failIfExists = failIfExists,
            context = context
        )
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
        val cryptoService = cryptoServiceFactory.getServiceRef(
            tenantId = tenantId,
            category = record.data.category
        )
        val signedBytes = cryptoService.sign(record, scheme, signatureSpec, data, context)
        return DigitalSignature.WithKey(
            by = record.publicKey,
            bytes = signedBytes,
            context = context
        )
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
        val cryptoService = cryptoServiceFactory.getServiceRef(
            tenantId = tenantId,
            category = record.data.category
        )
        return cryptoService.deriveSharedSecret(record, scheme, otherPublicKey, context)
    }

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
        val cryptoService = cryptoServiceFactory.getServiceRef(tenantId = tenantId, category = category)
        if (alias != null && store.find(tenantId, alias) != null) {
            throw IllegalStateException("The key with alias $alias already exist for tenant $tenantId")
        }
        val generatedKey = cryptoService.generateKeyPair(alias, scheme, context)
        store.save(tenantId, cryptoService.toSaveKeyContext(generatedKey, alias, scheme, externalId))
        return schemeMetadata.toSupportedPublicKey(generatedKey.publicKey)
    }

    private fun getOwnedKeyRecord(tenantId: String, publicKey: PublicKey): OwnedKeyRecord {
        if (publicKey is CompositeKey) {
            val leafKeysIdsChunks = publicKey.leafKeys.map {
                it.publicKeyId() to it
            }.chunked(KEY_LOOKUP_INPUT_ITEMS_LIMIT)
            for (chunk in leafKeysIdsChunks) {
                val found = store.lookup(tenantId, chunk.map { it.first })
                if (found.isNotEmpty()) {
                    for (key in chunk) {
                        val first = found.firstOrNull { it.id == key.first }
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
