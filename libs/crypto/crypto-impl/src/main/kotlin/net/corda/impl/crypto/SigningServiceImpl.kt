package net.corda.impl.crypto

import net.corda.internal.crypto.SigningService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.cipher.suite.schemes.SignatureSpec
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

    override val supportedSchemes: Array<SignatureScheme>
        get() = cryptoService.supportedSchemes()

    private val defaultSignatureScheme: SignatureScheme = schemeMetadata.findSignatureScheme(defaultSignatureSchemeCodeName)

    init {
        val map = cryptoService.supportedWrappingSchemes().map { it.codeName }
        if (defaultSignatureScheme.codeName !in map) {
            throw CryptoServiceException("The default signature schema '${defaultSignatureScheme.codeName}' is not supported, " +
                    "supported [${map.joinToString(", ")}]")
        }
    }

    override fun findPublicKey(alias: String): PublicKey? =
            cryptoService.findPublicKey(alias)

    @Suppress("TooGenericExceptionCaught")
    override fun generateKeyPair(alias: String): PublicKey =
            try {
                val publicKey = cryptoService.generateKeyPair(alias, defaultSignatureScheme)
                cache.save(publicKey, defaultSignatureScheme, alias)
                publicKey
            } catch (e: CryptoServiceException) {
                throw e
            } catch (e: Exception) {
                throw CryptoServiceException("Cannot generate key pair for alias $alias", e)
            }

    @Suppress("TooGenericExceptionCaught")
    override fun sign(publicKey: PublicKey, data: ByteArray): DigitalSignature.WithKey =
            try {
                val signingPublicKey = getSigningPublicKey(publicKey)
                val keyData = cache.find(signingPublicKey)
                        ?: throw CryptoServiceException("The entry for public key '${publicKey.toStringShort()}' is not found")
                val signatureScheme = schemeMetadata.findSignatureScheme(keyData.schemeCodeName)
                val signedBytes = if (keyData.alias != null) {
                    cryptoService.sign(keyData.alias!!, signatureScheme, signatureScheme.signatureSpec, data)
                } else {
                    throw CryptoServiceException("Cannot perform the sign operation as the key '${publicKey.toStringShort()}' is not supposed to be used by this service.")
                }
                DigitalSignature.WithKey(signingPublicKey, signedBytes)
            } catch (e: CryptoServiceException) {
                throw e
            } catch (e: Exception) {
                throw CryptoServiceException("Failed to sign using public key '${publicKey.toStringShort()}'", e)
            }

    @Suppress("TooGenericExceptionCaught")
    override fun sign(publicKey: PublicKey, signatureSpec: SignatureSpec, data: ByteArray): DigitalSignature.WithKey =
            try {
                val signingPublicKey = getSigningPublicKey(publicKey)
                val keyData = cache.find(signingPublicKey)
                        ?: throw CryptoServiceException("The entry for public key '${publicKey.toStringShort()}' is not found")
                val signatureScheme = schemeMetadata.findSignatureScheme(keyData.schemeCodeName)
                val signedBytes = if (keyData.alias != null) {
                    cryptoService.sign(keyData.alias!!, signatureScheme, signatureSpec, data)
                } else {
                    throw CryptoServiceException("Cannot perform the sign operation as the key '${publicKey.toStringShort()}' is not supposed to be used by this service.")
                }
                DigitalSignature.WithKey(signingPublicKey, signedBytes)
            } catch (e: CryptoServiceException) {
                throw e
            } catch (e: Exception) {
                throw CryptoServiceException("Failed to sign using public key '${publicKey.toStringShort()}'", e)
            }

    @Suppress("TooGenericExceptionCaught")
    override fun sign(alias: String, data: ByteArray): ByteArray=
            try {
                val keyData = cache.find(alias)
                        ?: throw CryptoServiceException("The entry for alias '$alias' is not found")
                val signatureScheme = schemeMetadata.findSignatureScheme(keyData.schemeCodeName)
                cryptoService.sign(alias, signatureScheme, signatureScheme.signatureSpec, data)
            } catch (e: CryptoServiceException) {
                throw e
            } catch (e: Exception) {
                throw CryptoServiceException("Failed to sign using key with alias $alias", e)
            }

    @Suppress("TooGenericExceptionCaught")
    override fun sign(alias: String, signatureSpec: SignatureSpec, data: ByteArray): ByteArray =
            try {
                val keyData = cache.find(alias)
                        ?: throw CryptoServiceException("The entry for alias '$alias' is not found")
                val signatureScheme = schemeMetadata.findSignatureScheme(keyData.schemeCodeName)
                cryptoService.sign(alias, signatureScheme, signatureSpec, data)
            } catch (e: CryptoServiceException) {
                throw e
            } catch (e: Exception) {
                throw CryptoServiceException("Failed to sign using key with alias $alias", e)
            }

    private fun getSigningPublicKey(publicKey: PublicKey): PublicKey {
        return publicKey.keys.first { cache.find(it) != null }
    }
}
