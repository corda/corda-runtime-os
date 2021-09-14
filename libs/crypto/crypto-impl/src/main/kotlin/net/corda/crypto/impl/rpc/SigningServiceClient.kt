package net.corda.crypto.impl.rpc

import net.corda.crypto.SigningService
import net.corda.data.WireKeyValuePair
import net.corda.data.crypto.wire.WireNoContentValue
import net.corda.data.crypto.wire.WirePublicKey
import net.corda.data.crypto.wire.WireRequestContext
import net.corda.data.crypto.wire.WireSignature
import net.corda.data.crypto.wire.WireSignatureSchemes
import net.corda.data.crypto.wire.WireSignatureSpec
import net.corda.data.crypto.wire.WireSignatureWithKey
import net.corda.data.crypto.wire.signing.WireSigningFindPublicKey
import net.corda.data.crypto.wire.signing.WireSigningGenerateKeyPair
import net.corda.data.crypto.wire.signing.WireSigningGetSupportedSchemes
import net.corda.data.crypto.wire.signing.WireSigningRequest
import net.corda.data.crypto.wire.signing.WireSigningResponse
import net.corda.data.crypto.wire.signing.WireSigningSign
import net.corda.data.crypto.wire.signing.WireSigningSignWithAlias
import net.corda.data.crypto.wire.signing.WireSigningSignWithAliasSpec
import net.corda.data.crypto.wire.signing.WireSigningSignWithSpec
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.cipher.suite.schemes.SignatureSpec
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.exceptions.CryptoServiceException
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import net.corda.v5.crypto.exceptions.CryptoServiceTimeoutException
import org.slf4j.Logger
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeoutException

@Suppress("TooGenericExceptionCaught")
class SigningServiceClient(
    private val memberId: String,
    private val requestingComponent: String,
    private val category: String,
    private val clientTimeout: Duration,
    private val clientRetries: Long,
    private val schemeMetadata: CipherSchemeMetadata,
    private val sender: RPCSender<WireSigningRequest, WireSigningResponse>
) : SigningService {
    companion object {
        const val CATEGORY = "category"
        private val GENERIC_EXCEPTION_MESSAGE = "Crypto operation failed"
        private val logger: Logger = contextLogger()
    }

    override val supportedSchemes: Array<SignatureScheme>
        get() {
            try {
                val request = createRequest(
                    WireSigningGetSupportedSchemes()
                )
                val response = request.executeWithTimeoutRetry(WireSignatureSchemes::class.java)
                return response!!.codes.map {
                    schemeMetadata.findSignatureScheme(it)
                }.toTypedArray()
            } catch (e: CryptoServiceLibraryException) {
                logger.error(GENERIC_EXCEPTION_MESSAGE, e)
                throw e
            } catch (e: Throwable) {
                logger.error(GENERIC_EXCEPTION_MESSAGE, e)
                throw CryptoServiceException(GENERIC_EXCEPTION_MESSAGE, e)
            }
        }

    override fun findPublicKey(alias: String): PublicKey? =
        try {
            val request = createRequest(
                WireSigningFindPublicKey(alias)
            )
            val response = request.executeWithTimeoutRetry(WirePublicKey::class.java, allowNoContentValue = true)
            if (response != null) {
                schemeMetadata.decodePublicKey(response.key.array())
            } else {
                null
            }
        } catch (e: CryptoServiceLibraryException) {
            logger.error(GENERIC_EXCEPTION_MESSAGE, e)
            throw e
        } catch (e: Throwable) {
            logger.error(GENERIC_EXCEPTION_MESSAGE, e)
            throw CryptoServiceException(GENERIC_EXCEPTION_MESSAGE, e)
        }

    override fun generateKeyPair(alias: String): PublicKey =
        try {
            val request = createRequest(
                WireSigningGenerateKeyPair(alias)
            )
            val response = request.executeWithTimeoutRetry(WirePublicKey::class.java)
            schemeMetadata.decodePublicKey(response!!.key.array())
        } catch (e: CryptoServiceLibraryException) {
            logger.error(GENERIC_EXCEPTION_MESSAGE, e)
            throw e
        } catch (e: Throwable) {
            logger.error(GENERIC_EXCEPTION_MESSAGE, e)
            throw CryptoServiceException(GENERIC_EXCEPTION_MESSAGE, e)
        }

    override fun sign(publicKey: PublicKey, data: ByteArray): DigitalSignature.WithKey =
        try {
            val request = createRequest(
                WireSigningSign(
                    ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                    ByteBuffer.wrap(data)
                )
            )
            val response = request.executeWithTimeoutRetry(WireSignatureWithKey::class.java)
            DigitalSignature.WithKey(
                by = schemeMetadata.decodePublicKey(response!!.publicKey.array()),
                bytes = response.bytes.array()
            )
        } catch (e: CryptoServiceLibraryException) {
            logger.error(GENERIC_EXCEPTION_MESSAGE, e)
            throw e
        } catch (e: Throwable) {
            logger.error(GENERIC_EXCEPTION_MESSAGE, e)
            throw CryptoServiceException(GENERIC_EXCEPTION_MESSAGE, e)
        }

    override fun sign(publicKey: PublicKey, signatureSpec: SignatureSpec, data: ByteArray): DigitalSignature.WithKey =
        try {
            val request = createRequest(
                WireSigningSignWithSpec(
                    ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                    WireSignatureSpec(signatureSpec.signatureName, signatureSpec.customDigestName?.name),
                    ByteBuffer.wrap(data)
                )
            )
            val response = request.executeWithTimeoutRetry(WireSignatureWithKey::class.java)
            DigitalSignature.WithKey(
                by = schemeMetadata.decodePublicKey(response!!.publicKey.array()),
                bytes = response.bytes.array()
            )
        } catch (e: CryptoServiceLibraryException) {
            logger.error(GENERIC_EXCEPTION_MESSAGE, e)
            throw e
        } catch (e: Throwable) {
            logger.error(GENERIC_EXCEPTION_MESSAGE, e)
            throw CryptoServiceException(GENERIC_EXCEPTION_MESSAGE, e)
        }

    override fun sign(alias: String, data: ByteArray): ByteArray =
        try {
            val request = createRequest(
                WireSigningSignWithAlias(
                    alias,
                    ByteBuffer.wrap(data)
                )
            )
            val response = request.executeWithTimeoutRetry(WireSignature::class.java)
            response!!.bytes.array()
        } catch (e: CryptoServiceLibraryException) {
            logger.error(GENERIC_EXCEPTION_MESSAGE, e)
            throw e
        } catch (e: Throwable) {
            logger.error(GENERIC_EXCEPTION_MESSAGE, e)
            throw CryptoServiceException(GENERIC_EXCEPTION_MESSAGE, e)
        }

    override fun sign(alias: String, signatureSpec: SignatureSpec, data: ByteArray): ByteArray =
        try {
            val request = createRequest(
                WireSigningSignWithAliasSpec(
                    alias,
                    WireSignatureSpec(signatureSpec.signatureName, signatureSpec.customDigestName?.name),
                    ByteBuffer.wrap(data)
                )
            )
            val response = request.executeWithTimeoutRetry(WireSignature::class.java)
            response!!.bytes.array()
        } catch (e: CryptoServiceLibraryException) {
            logger.error(GENERIC_EXCEPTION_MESSAGE, e)
            throw e
        } catch (e: Throwable) {
            logger.error(GENERIC_EXCEPTION_MESSAGE, e)
            throw CryptoServiceException(GENERIC_EXCEPTION_MESSAGE, e)
        }

    private fun createRequest(request: Any): WireSigningRequest = WireSigningRequest(
        createWireRequestContext(),
        request
    )

    private fun createWireRequestContext(): WireRequestContext = WireRequestContext(
        requestingComponent,
        Instant.now(),
        memberId,
        listOf(
            WireKeyValuePair(CATEGORY, category)
        )
    )

    @Suppress("ThrowsCount", "UNCHECKED_CAST")
    private fun <TRESP> WireSigningRequest.executeWithTimeoutRetry(
        respClazz: Class<TRESP>,
        allowNoContentValue: Boolean = false
    ): TRESP? {
        var retry = clientRetries
        while (true) {
            try {
                logger.info("Sending {} for member {}", request::class.java.name, context.memberId)
                val response = sender.sendRequest(this).getOrThrow(clientTimeout)
                if (response::class.java == WireNoContentValue::class.java && allowNoContentValue) {
                    logger.debug(
                        "Received empty response for {} for member {}",
                        request::class.java.name,
                        context.memberId
                    )
                    return null
                }
                require(
                    response.response != null &&
                            (response.response::class.java == respClazz) &&
                            response.context.memberId == context.memberId
                ) {
                    "Expected ${respClazz::class.java.name} for ${context.memberId} member, but " +
                            "received ${response.response::class.java.name} with ${response.context.memberId} member"
                }
                logger.debug("Received response {} for member {}", respClazz.name, context.memberId)
                return response.response as TRESP
            } catch (e: TimeoutException) {
                retry--
                if (retry <= 0) {
                    logger.error("Timeout executing ${request::class.java.name} for member ${context.memberId}", e)
                    throw CryptoServiceTimeoutException(clientTimeout)
                } else {
                    logger.error(
                        "Timeout executing ${request::class.java.name} for member ${context.memberId}, " +
                                "will retry...", e
                    )
                }
            } catch (e: CryptoServiceLibraryException) {
                logger.error("Failed executing ${request::class.java.name} for member ${context.memberId}", e)
                throw e
            } catch (e: Throwable) {
                val message = "Failed executing ${request::class.java.name} for member ${context.memberId}"
                logger.error(message, e)
                throw CryptoServiceException(message, e)
            }
        }
    }
}