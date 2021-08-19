package net.corda.crypto.impl

import net.corda.crypto.FreshKeySigningService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.WrappedPrivateKey
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.cipher.suite.schemes.SignatureSpec
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.exceptions.CryptoServiceException
import net.corda.v5.crypto.keys
import net.corda.v5.crypto.toStringShort
import java.security.PublicKey
import java.util.UUID

@Suppress("LongParameterList")
open class FreshKeySigningServiceImpl(
    private val cache: SigningKeyCache,
    private val cryptoService: CryptoService,
    private val freshKeysCryptoService: CryptoService,
    private val schemeMetadata: CipherSchemeMetadata,
    defaultFreshKeySignatureSchemeCodeName: String,
    private val masterWrappingKeyAlias: String = "wrapping-key-alias"
) : FreshKeySigningService {

    companion object {
        private val logger = contextLogger()
    }

    private val defaultFreshKeySignatureScheme: SignatureScheme = schemeMetadata.findSignatureScheme(defaultFreshKeySignatureSchemeCodeName)

    init {
        val freshKeyMap = freshKeysCryptoService.supportedWrappingSchemes().map { it.codeName }
        if (defaultFreshKeySignatureScheme.codeName !in freshKeyMap) {
            throw CryptoServiceException(
                "The default signature schema '${defaultFreshKeySignatureScheme.codeName}' is not supported, " +
                        "supported [${freshKeyMap.joinToString(", ")}]"
            )
        }
    }

    override fun freshKey(): PublicKey = generateFreshKey(null)

    override fun freshKey(externalId: UUID): PublicKey = generateFreshKey(externalId)

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
                freshKeysCryptoService.sign(wrappedPrivateKey, signatureScheme.signatureSpec, data)
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
                freshKeysCryptoService.sign(wrappedPrivateKey, signatureSpec, data)
            }
            DigitalSignature.WithKey(signingPublicKey, signedBytes)
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Exception) {
            throw CryptoServiceException("Failed to sign using public key '${publicKey.toStringShort()}'", e)
        }

    override fun ensureWrappingKey() {
        if (freshKeysCryptoService.requiresWrappingKey()) {
            freshKeysCryptoService.createWrappingKey(masterWrappingKeyAlias, false)
        } else {
            logger.info("The service doesn't require wrapping key.")
        }
    }

    private fun generateFreshKey(externalId: UUID?): PublicKey {
        val wrappedKeyPair = freshKeysCryptoService.generateWrappedKeyPair(masterWrappingKeyAlias, defaultFreshKeySignatureScheme)
        cache.save(wrappedKeyPair, masterWrappingKeyAlias, defaultFreshKeySignatureScheme, externalId)
        return wrappedKeyPair.publicKey
    }

    private fun getSigningPublicKey(publicKey: PublicKey): PublicKey {
        return publicKey.keys.first { cache.find(it) != null }
    }
}
