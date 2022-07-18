package net.corda.crypto.service.impl.signing

import net.corda.crypto.persistence.signing.SigningCachedKey
import net.corda.crypto.persistence.signing.SigningKeyOrderBy
import net.corda.crypto.persistence.signing.SigningKeyStore
import net.corda.crypto.persistence.signing.SigningKeyStoreActions
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.KeyOrderBy
import net.corda.crypto.service.SigningKeyInfo
import net.corda.crypto.service.SigningService
import net.corda.crypto.service.impl.hsm.soft.deriveSharedSecret
import net.corda.crypto.service.impl.hsm.soft.generateKeyPair
import net.corda.crypto.service.impl.hsm.soft.getSupportedSchemes
import net.corda.crypto.service.impl.hsm.soft.sign
import net.corda.crypto.service.impl.hsm.soft.toSaveKeyContext
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
        return cryptoServiceFactory.getInstance(tenantId = tenantId, category = category).getSupportedSchemes()
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
        return store.act(tenantId) {
            it.lookup(
                skip,
                take,
                orderBy.toSigningKeyOrderBy(),
                filter
            ).map { key -> key.toSigningKeyInfo() }
        }
    }

    override fun lookup(tenantId: String, ids: List<String>): Collection<SigningKeyInfo> {
        logger.debug { "lookup(tenantId=$tenantId, ids=[${ids.joinToString()}])" }
        require(ids.size <= KEY_LOOKUP_INPUT_ITEMS_LIMIT) {
            "The number of items exceeds $KEY_LOOKUP_INPUT_ITEMS_LIMIT"
        }
        return store.act(tenantId) {
            it.lookup(ids).map { key -> key.toSigningKeyInfo() }
        }
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
        val record = store.act(tenantId) {
            getKeyRecord(tenantId, it, publicKey)
        }
        logger.debug { "sign(tenant=$tenantId, publicKey=${record.second.id})"  }
        val scheme = schemeMetadata.findKeyScheme(record.second.schemeCodeName)
        val cryptoService = cryptoServiceFactory.getInstance(
            tenantId = tenantId,
            category = record.second.category,
            associationId = record.second.associationId
        )
        val signedBytes = cryptoService.sign(record.second, scheme, signatureSpec, data, context)
        return DigitalSignature.WithKey(
            by = record.first,
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
        val record = store.act(tenantId) {
            getKeyRecord(tenantId, it, publicKey)
        }
        logger.info(
            "deriveSharedSecret(tenant={}, publicKey={}, otherPublicKey={})",
            tenantId,
            record.second.id,
            otherPublicKey.publicKeyId()
        )
        val scheme = schemeMetadata.findKeyScheme(record.second.schemeCodeName)
        val cryptoService = cryptoServiceFactory.getInstance(
            tenantId = tenantId,
            category = record.second.category,
            associationId = record.second.associationId
        )
        return cryptoService.deriveSharedSecret(record.second, scheme, otherPublicKey, context)
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
        val cryptoService = cryptoServiceFactory.getInstance(tenantId = tenantId, category = category)
        store.act(tenantId) {
            if (alias != null && it.find(alias) != null) {
                throw IllegalStateException(
                    "The key with alias $alias already exist for tenant $tenantId"
                )
            }
        }
        val generatedKey = cryptoService.generateKeyPair(alias, scheme, context)
        store.act(tenantId) {
            it.save(cryptoService.toSaveKeyContext(generatedKey, alias, scheme, externalId))
        }
        return schemeMetadata.toSupportedPublicKey(generatedKey.publicKey)
    }

    private fun getKeyRecord(
        tenantId: String,
        storeActions: SigningKeyStoreActions,
        publicKey: PublicKey
    ): Pair<PublicKey, SigningCachedKey> =
        if (publicKey is CompositeKey) {
            var result: Pair<PublicKey, SigningCachedKey>? = null
            publicKey.leafKeys.firstOrNull {
                val r = storeActions.find(it)
                if (r != null) {
                    result = it to r
                    true
                } else {
                    false
                }
            }
            result
        } else {
            storeActions.find(publicKey)?.let { publicKey to it }
        } ?: throw IllegalArgumentException(
            "The tenant $tenantId doesn't own public key '${publicKey.publicKeyId()}'."
        )

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
