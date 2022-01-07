package net.corda.crypto.impl

import net.corda.crypto.impl.persistence.SigningKeyCache
import net.corda.crypto.SigningService
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

open class SigningServiceImpl(
    private val cache: SigningKeyCache,
    private val cryptoServiceFactory: CryptoServiceFactory,
    private val schemeMetadata: CipherSchemeMetadata,
    override val tenantId: String
) : SigningService {
    companion object {
        private val logger = contextLogger()
    }

    override fun getSupportedSchemes(category: String): List<String> {
        logger.info("getSupportedSchemes(category={})", category)
        return cryptoService(category).getSupportedSchemes()
    }

    override fun findPublicKey(alias: String): PublicKey? {
        logger.info("findPublicKey(alias={})", alias)
        val publicKey = cache.find(alias)?.publicKey
        return if(publicKey != null) {
            schemeMetadata.decodePublicKey(publicKey)
        } else {
            null
        }
    }

    override fun generateKeyPair(category: String, alias: String, context: Map<String, String>): PublicKey =
        try {
            logger.info("generateKeyPair(category={}, alias={})", category, alias)
            val cryptoService = cryptoService(category)
            val publicKey = cryptoService.generateKeyPair(alias, context)
            cache.save(
                publicKey = publicKey,
                scheme = cryptoService.defaultSignatureScheme,
                category = category,
                alias = alias)
            publicKey
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("Cannot generate key pair for category=$category and alias=$alias", e)
        }

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

    private fun getSigningPublicKey(publicKey: PublicKey): PublicKey =
        publicKey.keys.firstOrNull { cache.find(it) != null }
            ?: throw CryptoServiceBadRequestException(
                "The member doesn't own public key '${publicKey.toStringShort()}'."
            )

    private fun doSign(
        publicKey: PublicKey,
        signatureSpec: SignatureSpec?,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey =
        try {
            logger.info("Signing using public key={}", publicKey.toStringShort())
            val signingPublicKey = getSigningPublicKey(publicKey)
            val keyData = cache.find(signingPublicKey)
                ?: throw CryptoServiceBadRequestException(
                    "The entry for public key '${publicKey.toStringShort()}' is not found"
                )
            var signatureScheme = schemeMetadata.findSignatureScheme(keyData.schemeCodeName)
            if(signatureSpec != null) {
                signatureScheme = signatureScheme.copy(signatureSpec = signatureSpec)
            }
            val signedBytes = if (keyData.alias != null) {
                cryptoService.sign(
                    keyData.alias!!,
                    signatureScheme,
                    data,
                    context
                )
            } else {
                if (keyData.privateKeyMaterial == null || keyData.masterKeyAlias.isNullOrBlank()) {
                    throw IllegalArgumentException(
                        "Cannot perform the sign operation as either the key material is absent or the master key alias."
                    )
                }
                val wrappedPrivateKey = WrappedPrivateKey(
                    keyMaterial = keyData.privateKeyMaterial!!,
                    masterKeyAlias = keyData.masterKeyAlias!!,
                    signatureScheme = signatureScheme,
                    encodingVersion = keyData.version
                )
                cryptoService.sign(
                    wrappedPrivateKey,
                    data,
                    context
                )
            }
            DigitalSignature.WithKey(signingPublicKey, signedBytes)
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("Failed to sign using public key '${publicKey.toStringShort()}'", e)
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
                ?: throw CryptoServiceBadRequestException("The entry for alias '$alias' is not found")
            var signatureScheme = schemeMetadata.findSignatureScheme(keyData.schemeCodeName)
            if(signatureSpec != null) {
                signatureScheme = signatureScheme.copy(signatureSpec = signatureSpec)
            }
            cryptoService.sign(
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

    private fun cryptoService(category: String): CryptoServiceConfiguredInstance =
        cryptoServiceFactory.getInstance(tenantId = tenantId, category = category)
}
