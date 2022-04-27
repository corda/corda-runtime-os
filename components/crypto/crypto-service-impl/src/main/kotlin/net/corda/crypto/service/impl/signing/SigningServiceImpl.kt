package net.corda.crypto.service.impl.signing

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyCache
import net.corda.crypto.persistence.SigningKeyCacheActions
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.service.CryptoServiceRef
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.KeyOrderBy
import net.corda.crypto.service.SigningKeyInfo
import net.corda.crypto.service.SigningService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.KEY_LOOKUP_INPUT_ITEMS_LIMIT
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceException
import net.corda.v5.crypto.publicKeyId
import java.security.PublicKey

@Suppress("TooManyFunctions")
open class SigningServiceImpl(
    private val cache: SigningKeyCache,
    private val cryptoServiceFactory: CryptoServiceFactory,
    override val schemeMetadata: CipherSchemeMetadata
) : SigningService {
    companion object {
        private val logger = contextLogger()
    }

    override fun getSupportedSchemes(tenantId: String, category: String): List<String> {
        logger.debug("getSupportedSchemes(tenant={}, category={})", tenantId, category)
        return getCryptoService(tenantId, category).getSupportedSchemes()
    }

    override fun lookup(
        tenantId: String,
        skip: Int,
        take: Int,
        orderBy: KeyOrderBy,
        filter: Map<String, String>
    ): Collection<SigningKeyInfo> {
        logger.debug(
            "lookup(tenantId={}, skip={}, take={}, orderBy={}, filter=[{}]",
            skip, take, orderBy, tenantId, filter.map { it }.joinToString { "${it.key}=${it.value}" }
        )
        return cache.act(tenantId) {
            it.lookup(
                skip,
                take,
                orderBy.toSigningKeyOrderBy(),
                filter
            ).map { key -> key.toSigningKeyInfo() }
        }
    }

    override fun lookup(tenantId: String, ids: List<String>): Collection<SigningKeyInfo> {
        logger.debug("lookup(tenantId={}, ids=[{}])", tenantId, ids.joinToString())
        require(ids.size <= KEY_LOOKUP_INPUT_ITEMS_LIMIT) {
            "The number of items exceeds $KEY_LOOKUP_INPUT_ITEMS_LIMIT"
        }
        return cache.act(tenantId) {
            it.lookup(ids).map { key -> key.toSigningKeyInfo() }
        }
    }

    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        context: Map<String, String>
    ): PublicKey =
        doGenerateKeyPair(
            tenantId = tenantId,
            category = category,
            alias = alias,
            externalId = null,
            context = context
        )

    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        externalId: String,
        context: Map<String, String>
    ): PublicKey =
        doGenerateKeyPair(
            tenantId = tenantId,
            category = category,
            alias = alias,
            externalId = externalId,
            context = context
        )

    override fun freshKey(tenantId: String, context: Map<String, String>): PublicKey =
        doGenerateKeyPair(
            tenantId = tenantId,
            category = CryptoConsts.HsmCategories.FRESH_KEYS,
            alias = null,
            externalId = null,
            context = context
        )

    override fun freshKey(tenantId: String, externalId: String, context: Map<String, String>): PublicKey =
        doGenerateKeyPair(
            tenantId = tenantId,
            category = CryptoConsts.HsmCategories.FRESH_KEYS,
            alias = null,
            externalId = externalId,
            context = context
        )

    override fun sign(
        tenantId: String,
        publicKey: PublicKey,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey =
        doSign(tenantId, publicKey, null, data, context)

    override fun sign(
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey =
        doSign(tenantId, publicKey, signatureSpec, data, context)

    private fun doGenerateKeyPair(
        tenantId: String,
        category: String,
        alias: String?,
        externalId: String?,
        context: Map<String, String>
    ): PublicKey =
        try {
            logger.info("generateKeyPair(tenant={}, category={}, alias={}))", tenantId, category, alias)
            val cryptoService = getCryptoService(tenantId, category)
            cache.act(tenantId) {
                if (alias != null && it.find(alias) != null) {
                    throw CryptoServiceBadRequestException(
                        "The key with alias $alias already exist for tenant $tenantId"
                    )
                }
                val generatedKey = cryptoService.generateKeyPair(alias, context)
                it.save(cryptoService.toSaveKeyContext(generatedKey, alias, externalId))
                generatedKey.publicKey
            }
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException(
                "Cannot generate key pair for category=$category and alias=$alias, tenant=$tenantId", e
            )
        }

    private fun doSign(
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec?,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey =
        try {
            cache.act(tenantId) {
                val record = getKeyRecord(tenantId, it, publicKey)
                logger.info("sign(tenant={}, publicKey={})", tenantId, record.second.id)
                var signatureScheme = schemeMetadata.findSignatureScheme(record.second.schemeCodeName)
                if (signatureSpec != null) {
                    signatureScheme = signatureScheme.copy(signatureSpec = signatureSpec)
                }
                val cryptoService = getCryptoService(tenantId, record.second.category)
                val signedBytes = cryptoService.sign(record.second, signatureScheme, data, context)
                DigitalSignature.WithKey(record.first, signedBytes)
            }
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException(
                "Failed to sign using public key '${publicKey.publicKeyId()}' for tenant $tenantId",
                e
            )
        }

    private fun getKeyRecord(
        tenantId: String,
        cacheActions: SigningKeyCacheActions,
        publicKey: PublicKey
    ): Pair<PublicKey, SigningCachedKey> =
        if (publicKey is CompositeKey) {
            var result: Pair<PublicKey, SigningCachedKey>? = null
            publicKey.leafKeys.firstOrNull {
                val r = cacheActions.find(it)
                if (r != null) {
                    result = it to r
                    true
                } else {
                    false
                }
            }
            result
        } else {
            cacheActions.find(publicKey)?.let { publicKey to it }
        } ?: throw CryptoServiceBadRequestException(
            "The tenant $tenantId doesn't own public key '${publicKey.publicKeyId()}'."
        )

    private fun getCryptoService(tenantId: String, category: String): CryptoServiceRef =
        cryptoServiceFactory.getInstance(tenantId = tenantId, category = category)

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
            created = created
        )
}
