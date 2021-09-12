package net.corda.components.crypto.services

import net.corda.components.crypto.services.persistence.SigningKeyCache
import net.corda.crypto.FreshKeySigningService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.WrappedPrivateKey
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.cipher.suite.schemes.SignatureSpec
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
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
        logger.info("Initializing with default scheme $defaultFreshKeySignatureSchemeCodeName and master alias=$masterWrappingKeyAlias")
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

    override fun sign(publicKey: PublicKey, data: ByteArray): DigitalSignature.WithKey =
        doSign(publicKey, null, data)

    override fun sign(publicKey: PublicKey, signatureSpec: SignatureSpec, data: ByteArray): DigitalSignature.WithKey =
        doSign(publicKey, signatureSpec, data)

    override fun ensureWrappingKey() {
        if (freshKeysCryptoService.requiresWrappingKey()) {
            logger.info("Ensuring that the wrapping key $masterWrappingKeyAlias exists.")
            freshKeysCryptoService.createWrappingKey(masterWrappingKeyAlias, false)
        } else {
            logger.info("The service doesn't require wrapping key.")
        }
    }

    override fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> =
        throw NotImplementedError("It's not implemented yet.")

    private fun generateFreshKey(externalId: UUID?): PublicKey {
        logger.info("Generating fresh key for externalId=${externalId ?: "null"}")
        val wrappedKeyPair = freshKeysCryptoService.generateWrappedKeyPair(masterWrappingKeyAlias, defaultFreshKeySignatureScheme)
        cache.save(wrappedKeyPair, masterWrappingKeyAlias, defaultFreshKeySignatureScheme, externalId)
        return wrappedKeyPair.publicKey
    }

    @Suppress("TooGenericExceptionCaught")
    private fun doSign(publicKey: PublicKey, signatureSpec: SignatureSpec?, data: ByteArray): DigitalSignature.WithKey =
        try {
            logger.info("Signing using public key={}", publicKey.toStringShort())
            val signingPublicKey = getSigningPublicKey(publicKey)
            val keyData = cache.find(signingPublicKey)
                ?: throw CryptoServiceBadRequestException("The entry for public key '${publicKey.toStringShort()}' is not found")
            val signatureScheme = schemeMetadata.findSignatureScheme(keyData.schemeCodeName)
            val signedBytes = if (keyData.alias != null) {
                cryptoService.sign(keyData.alias!!, signatureScheme, signatureSpec ?: signatureScheme.signatureSpec, data)
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
                freshKeysCryptoService.sign(wrappedPrivateKey, signatureSpec ?: signatureScheme.signatureSpec, data)
            }
            DigitalSignature.WithKey(signingPublicKey, signedBytes)
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("Failed to sign using public key '${publicKey.toStringShort()}'", e)
        }

    private fun getSigningPublicKey(publicKey: PublicKey): PublicKey =
        publicKey.keys.firstOrNull { cache.find(it) != null }
            ?: throw CryptoServiceBadRequestException("The member doesn't own public key '${publicKey.toStringShort()}'.")
}
