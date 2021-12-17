package net.corda.crypto.impl

import net.corda.crypto.impl.persistence.SigningKeyCache
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

    private val defaultFreshKeySignatureScheme: SignatureScheme =
        schemeMetadata.findSignatureScheme(defaultFreshKeySignatureSchemeCodeName)

    init {
        logger.info(
            "Initializing with default scheme {} and master alias=$masterWrappingKeyAlias",
            defaultFreshKeySignatureSchemeCodeName
        )
        val freshKeyMap = freshKeysCryptoService.supportedWrappingSchemes().map { it.codeName }
        if (defaultFreshKeySignatureScheme.codeName !in freshKeyMap) {
            throw CryptoServiceException(
                "The default signature schema '${defaultFreshKeySignatureScheme.codeName}' is not supported, " +
                        "supported [${freshKeyMap.joinToString(", ")}]"
            )
        }
    }

    override fun freshKey(context: Map<String, String>): PublicKey = generateFreshKey(null, context)

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

    private fun generateFreshKey(externalId: UUID?, context: Map<String, String>): PublicKey {
        logger.info("Generating fresh key for externalId=${externalId ?: "null"}")
        val wrappedKeyPair = freshKeysCryptoService.generateWrappedKeyPair(
            masterWrappingKeyAlias,
            defaultFreshKeySignatureScheme,
            context
        )
        cache.save(wrappedKeyPair, masterWrappingKeyAlias, defaultFreshKeySignatureScheme, externalId)
        return wrappedKeyPair.publicKey
    }

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
                cryptoService.sign(keyData.alias!!, signatureScheme, data, context)
            } else {
                if (keyData.privateKeyMaterial == null || keyData.masterKeyAlias.isNullOrBlank()) {
                    throw IllegalArgumentException(
                        "Can't perform the sign operation as either the key material is absent or the master key alias."
                    )
                }
                val wrappedPrivateKey = WrappedPrivateKey(
                    keyMaterial = keyData.privateKeyMaterial!!,
                    masterKeyAlias = keyData.masterKeyAlias!!,
                    signatureScheme = signatureScheme,
                    encodingVersion = keyData.version
                )
                freshKeysCryptoService.sign(wrappedPrivateKey, data, context)
            }
            DigitalSignature.WithKey(signingPublicKey, signedBytes)
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("Failed to sign using public key '${publicKey.toStringShort()}'", e)
        }

    private fun getSigningPublicKey(publicKey: PublicKey): PublicKey =
        publicKey.keys.firstOrNull { cache.find(it) != null }
            ?: throw CryptoServiceBadRequestException(
                "The member doesn't own public key '${publicKey.toStringShort()}'."
            )
}
