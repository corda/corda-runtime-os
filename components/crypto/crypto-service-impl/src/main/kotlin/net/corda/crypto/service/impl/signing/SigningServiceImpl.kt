package net.corda.crypto.service.impl.signing

import net.corda.crypto.CryptoConsts
import net.corda.crypto.service.CryptoServiceConfiguredInstance
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.SigningService
import net.corda.crypto.service.impl.persistence.SigningKeyCache
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.WrappedPrivateKey
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceException
import net.corda.v5.crypto.keys
import net.corda.v5.crypto.toStringShort
import java.security.PublicKey
import java.util.UUID

@Suppress("TooManyFunctions")
open class SigningServiceImpl(
    private val cache: SigningKeyCache,
    private val cryptoServiceFactory: CryptoServiceFactory,
    override val schemeMetadata: CipherSchemeMetadata,
    override val tenantId: String
) : SigningService {
    companion object {
        private val logger = contextLogger()
    }

    override fun getSupportedSchemes(category: String): List<String> {
        logger.info("getSupportedSchemes(category={}), tenant={}", category, tenantId)
        return cryptoService(category).getSupportedSchemes()
    }

    override fun findPublicKey(alias: String): PublicKey? {
        logger.info("findPublicKey(alias={}), tenant={}", alias, tenantId)
        val publicKey = cache.find(alias)?.publicKey
        return if(publicKey != null) {
            schemeMetadata.decodePublicKey(publicKey.array())
        } else {
            null
        }
    }

    override fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> {
        throw NotImplementedError("It's not implemented yet.")
    }

    override fun generateKeyPair(category: String, alias: String, context: Map<String, String>): PublicKey =
        try {
            logger.info("generateKeyPair(category={}, alias={}), tenant={}", category, alias, tenantId)
            val cryptoService = cryptoService(category)
            val publicKey = cryptoService.generateKeyPair(alias, context)
            cache.save(
                publicKey = publicKey,
                scheme = cryptoService.defaultSignatureScheme,
                category = category,
                alias = alias,
                hsmAlias = cryptoService.computeHSMAlias(alias)
            )
            publicKey
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException(
                "Cannot generate key pair for category=$category and alias=$alias, tenant=$tenantId", e
            )
        }

    override fun freshKey(context: Map<String, String>): PublicKey =
        generateFreshKey(null, context)

    override fun freshKey(externalId: UUID, context: Map<String, String>): PublicKey =
        generateFreshKey(externalId, context)

    override fun sign(publicKey: PublicKey, data: ByteArray, context: Map<String, String>): DigitalSignature.WithKey =
        doSign(publicKey, null, data, context)

    override fun sign(
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey =
        doSign(publicKey, signatureSpec, data, context)

    override fun sign(alias: String, data: ByteArray, context: Map<String, String>): ByteArray =
        doSign(alias, null, data, context)

    override fun sign(
        alias: String,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String>
    ): ByteArray =
        doSign(alias, signatureSpec, data, context)

    private fun doSign(
        publicKey: PublicKey,
        signatureSpec: SignatureSpec?,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey =
        try {
            logger.info("Signing using public key={}, tenant={}", publicKey.toStringShort(), tenantId)
            val signingPublicKey = getSigningPublicKey(publicKey)
            val keyData = cache.find(signingPublicKey)
                ?: throw CryptoServiceBadRequestException(
                    "The entry for public key '${publicKey.toStringShort()}' is not found for tenant=$tenantId"
                )
            var signatureScheme = schemeMetadata.findSignatureScheme(keyData.schemeCodeName)
            if(signatureSpec != null) {
                signatureScheme = signatureScheme.copy(signatureSpec = signatureSpec)
            }
            val signedBytes = if (keyData.alias != null) {
                cryptoService(keyData.category).sign(
                    keyData.alias!!,
                    signatureScheme,
                    data,
                    context
                )
            } else {
                if (keyData.privateKeyMaterial == null || keyData.masterKeyAlias.isNullOrBlank()) {
                    throw IllegalArgumentException(
                        "Cannot perform the sign operation for public key=${publicKey.toStringShort()} " +
                                "and tenant=$tenantId as either the key material is absent or the master key alias."
                    )
                }
                val wrappedPrivateKey = WrappedPrivateKey(
                    keyMaterial = keyData.privateKeyMaterial!!.array(),
                    masterKeyAlias = keyData.masterKeyAlias!!,
                    signatureScheme = signatureScheme,
                    encodingVersion = keyData.version
                )
                cryptoService(keyData.category).sign(
                    wrappedPrivateKey,
                    data,
                    context
                )
            }
            DigitalSignature.WithKey(signingPublicKey, signedBytes)
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException(
                "Failed to sign using public key '${publicKey.toStringShort()}' for tenant $tenantId",
                e
            )
        }

    private fun generateFreshKey(externalId: UUID?, context: Map<String, String>): PublicKey {
        logger.info("Generating fresh key for tenant=$tenantId and externalId=${externalId ?: "null"}")
        val cryptoService = cryptoService(CryptoConsts.Categories.FRESH_KEYS)
        val wrappedKeyPair = cryptoService.generateWrappedKeyPair(context)
        cache.save(
            wrappedKeyPair,
            cryptoService.wrappingKeyAlias,
            cryptoService.defaultSignatureScheme,
            externalId
        )
        return wrappedKeyPair.publicKey
    }

    private fun doSign(
        alias: String,
        signatureSpec: SignatureSpec?,
        data: ByteArray,
        context: Map<String, String>
    ): ByteArray =
        try {
            logger.info("Signing using alias={}", alias)
            val keyData = cache.find(alias)
                ?: throw CryptoServiceBadRequestException(
                    "The entry for alias '$alias' is not found for tenant $tenantId"
                )
            var signatureScheme = schemeMetadata.findSignatureScheme(keyData.schemeCodeName)
            if(signatureSpec != null) {
                signatureScheme = signatureScheme.copy(signatureSpec = signatureSpec)
            }
            cryptoService(keyData.category).sign(
                alias,
                signatureScheme,
                data,
                context
            )
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("Failed to sign using key with alias $alias", e)
        }

    private fun getSigningPublicKey(publicKey: PublicKey): PublicKey =
        publicKey.keys.firstOrNull { cache.find(it) != null }
            ?: throw CryptoServiceBadRequestException(
                "The tenant $tenantId doesn't own public key '${publicKey.toStringShort()}'."
            )

    private fun cryptoService(category: String): CryptoServiceConfiguredInstance =
        cryptoServiceFactory.getInstance(tenantId = tenantId, category = category)
}
