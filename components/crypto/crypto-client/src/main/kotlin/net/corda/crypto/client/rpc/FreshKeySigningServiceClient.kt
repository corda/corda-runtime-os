package net.corda.crypto.client.rpc

import net.corda.crypto.FreshKeySigningService
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.WireNoContentValue
import net.corda.data.crypto.wire.WirePublicKey
import net.corda.data.crypto.wire.WirePublicKeys
import net.corda.data.crypto.wire.WireRequestContext
import net.corda.data.crypto.wire.WireSignatureSpec
import net.corda.data.crypto.wire.WireSignatureWithKey
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysEnsureWrappingKey
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysFilterMyKeys
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysFreshKey
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysRequest
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysResponse
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysSign
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysSignWithSpec
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.exceptions.CryptoServiceException
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import net.corda.v5.crypto.exceptions.CryptoServiceTimeoutException
import org.slf4j.Logger
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeoutException

@Suppress("LongParameterList")
class FreshKeySigningServiceClient(
    private val memberId: String,
    private val requestingComponent: String,
    private val clientTimeout: Duration,
    private val clientRetries: Long,
    private val schemeMetadata: CipherSchemeMetadata,
    private val sender: RPCSender<WireFreshKeysRequest, WireFreshKeysResponse>
) : FreshKeySigningService {
    companion object {
        private val logger: Logger = contextLogger()
    }

    override fun freshKey(context: Map<String, String>): PublicKey {
        val request = createRequest(
            WireFreshKeysFreshKey(null, context.toWire())
        )
        val response = request.executeWithTimeoutRetry(WirePublicKey::class.java)
        return schemeMetadata.decodePublicKey(response.key.array())
    }

    override fun freshKey(externalId: UUID, context: Map<String, String>): PublicKey {
        val request = createRequest(
            WireFreshKeysFreshKey(externalId.toString(), context.toWire())
        )
        val response = request.executeWithTimeoutRetry(WirePublicKey::class.java)
        return schemeMetadata.decodePublicKey(response.key.array())
    }

    override fun sign(
        publicKey: PublicKey,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey {
        val request = createRequest(
            WireFreshKeysSign(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                ByteBuffer.wrap(data),
                context.toWire()
            )
        )
        val response = request.executeWithTimeoutRetry(WireSignatureWithKey::class.java)
        return DigitalSignature.WithKey(
            by = schemeMetadata.decodePublicKey(response.publicKey.array()),
            bytes = response.bytes.array()
        )
    }

    override fun sign(
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey {
        val request = createRequest(
            WireFreshKeysSignWithSpec(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                WireSignatureSpec(signatureSpec.signatureName, signatureSpec.customDigestName?.name),
                ByteBuffer.wrap(data),
                context.toWire()
            )
        )
        val response = request.executeWithTimeoutRetry(WireSignatureWithKey::class.java)
        return DigitalSignature.WithKey(
            by = schemeMetadata.decodePublicKey(response.publicKey.array()),
            bytes = response.bytes.array()
        )
    }

    override fun ensureWrappingKey() {
        val request = createRequest(
            WireFreshKeysEnsureWrappingKey()
        )
        request.executeWithTimeoutRetry(WireNoContentValue::class.java)
    }

    override fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> {
        val request = createRequest(
            WireFreshKeysFilterMyKeys(
                candidateKeys.map {
                    ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(it))
                }
            )
        )
        val response = request.executeWithTimeoutRetry(WirePublicKeys::class.java)
        return response.keys.map {
            schemeMetadata.decodePublicKey(it.array())
        }
    }

    private fun createRequest(request: Any): WireFreshKeysRequest = WireFreshKeysRequest(
        createWireRequestContext(),
        request
    )

    private fun createWireRequestContext(): WireRequestContext = WireRequestContext(
        requestingComponent,
        Instant.now(),
        memberId,
        KeyValuePairList(emptyList())
    )

    @Suppress("ThrowsCount", "UNCHECKED_CAST", "ComplexMethod")
    private fun <RESPONSE> WireFreshKeysRequest.executeWithTimeoutRetry(
        respClazz: Class<RESPONSE>
    ): RESPONSE {
        var retry = clientRetries
        while (true) {
            try {
                logger.info("Sending {} for member {}", request::class.java.name, context.memberId)
                val response = sender.sendRequest(this).getOrThrow(clientTimeout)
                require(
                    response.response != null &&
                            (response.response::class.java == respClazz) &&
                            response.context.requestingComponent == context.requestingComponent &&
                            response.context.memberId == context.memberId
                ) {
                    "Expected ${respClazz.name} for ${context.memberId} member, but " +
                            "received ${response.response::class.java.name} with ${response.context.memberId} member"
                }
                logger.debug("Received response {} for member {}", respClazz.name, context.memberId)
                return response.response as RESPONSE
            } catch (e: TimeoutException) {
                retry--
                if (retry < 0) {
                    logger.error("Timeout executing ${request::class.java.name} for member ${context.memberId}, " +
                            "all retries are exhausted", e)
                    throw CryptoServiceTimeoutException(clientTimeout, e)
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