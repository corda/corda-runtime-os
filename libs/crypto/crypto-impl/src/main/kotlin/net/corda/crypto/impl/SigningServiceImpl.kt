package net.corda.crypto.impl

import net.corda.crypto.impl.persistence.SigningKeyCache
import net.corda.crypto.SigningService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.WrappedPrivateKey
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceException
import net.corda.v5.crypto.keys
import net.corda.v5.crypto.toStringShort
import java.security.PublicKey

open class SigningServiceImpl(
    private val cache: SigningKeyCache,
    private val cryptoService: CryptoService,
    private val schemeMetadata: CipherSchemeMetadata,
    defaultSignatureSchemeCodeName: String
) : SigningService {
    companion object {
        private val logger = contextLogger()
    }

    override val supportedSchemes: Array<SignatureScheme>
        get() = cryptoService.supportedSchemes()

    private val defaultSignatureScheme: SignatureScheme =
        schemeMetadata.findSignatureScheme(defaultSignatureSchemeCodeName)

    init {
        logger.info("Initializing with default scheme $defaultSignatureSchemeCodeName")
        val map = cryptoService.supportedWrappingSchemes().map { it.codeName }
        if (defaultSignatureScheme.codeName !in map) {
            throw CryptoServiceException(
                "The default signature schema '${defaultSignatureScheme.codeName}' is not supported, " +
                        "supported [${map.joinToString(", ")}]"
            )
        }
    }

    override fun findPublicKey(alias: String): PublicKey? =
        cryptoService.findPublicKey(alias)

    override fun generateKeyPair(alias: String, context: Map<String, String>): PublicKey =
        try {
            logger.info("Generating key pair for alias={}", alias)
            val publicKey = cryptoService.generateKeyPair(alias, defaultSignatureScheme, context)
            cache.save(publicKey, defaultSignatureScheme, alias)
            publicKey
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("Cannot generate key pair for alias $alias", e)
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
}
