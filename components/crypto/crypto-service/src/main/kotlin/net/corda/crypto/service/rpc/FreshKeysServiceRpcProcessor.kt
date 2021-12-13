package net.corda.crypto.service.rpc

import net.corda.crypto.service.CryptoFactory
import net.corda.crypto.FreshKeySigningService
import net.corda.data.crypto.wire.WireNoContentValue
import net.corda.data.crypto.wire.WirePublicKey
import net.corda.data.crypto.wire.WirePublicKeys
import net.corda.data.crypto.wire.WireRequestContext
import net.corda.data.crypto.wire.WireResponseContext
import net.corda.data.crypto.wire.WireSignatureWithKey
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysEnsureWrappingKey
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysFilterMyKeys
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysFreshKey
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysRequest
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysResponse
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysSign
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysSignWithSpec
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.slf4j.Logger
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

class FreshKeysServiceRpcProcessor(
    private val cryptoFactory: CryptoFactory
) : RPCResponderProcessor<WireFreshKeysRequest, WireFreshKeysResponse> {

    private companion object {
        val logger: Logger = contextLogger()

        val handlers = mapOf<Class<*>, Class<out CryptoRpcHandler<*, *>>>(
            WireFreshKeysFreshKey::class.java to WireFreshKeysFreshKeyHandler::class.java,
            WireFreshKeysEnsureWrappingKey::class.java to WireFreshKeysEnsureWrappingKeyHandler::class.java,
            WireFreshKeysSign::class.java to WireFreshKeysSignHandler::class.java,
            WireFreshKeysSignWithSpec::class.java to WireFreshKeysSignWithSpecHandler::class.java,
            WireFreshKeysFilterMyKeys::class.java to WireFreshKeysFilterMyKeysHandler::class.java,
        )
    }

    override fun onNext(request: WireFreshKeysRequest, respFuture: CompletableFuture<WireFreshKeysResponse>) {
        try {
            logger.info("Handling {} for member {}", request.request::class.java.name, request.context.memberId)
            val result = WireFreshKeysResponse(
                WireResponseContext(
                    request.context.requestingComponent,
                    request.context.requestTimestamp,
                    Instant.now(),
                    request.context.memberId,
                    request.context.other
                ),
                getHandler(request).handle(request.context, request.request)
            )
            logger.debug(
                "Handled {} for member {} with {}",
                request.request::class.java.name,
                request.context.memberId,
                if(result.response != null) result.response::class.java.name else "null"
            )
            respFuture.complete(result)
        } catch (e: Throwable) {
            val message = "Failed to handle ${request.request::class.java.name} for member ${request.context.memberId}"
            logger.error(message, e)
            respFuture.completeExceptionally(CryptoServiceLibraryException(message, e))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getHandler(request: WireFreshKeysRequest): CryptoRpcHandler<Any, Any> {
        val type = handlers[request.request::class.java] ?: throw CryptoServiceBadRequestException(
            "Unknown request type ${request.request::class.java.name}"
        )
        val freshKeysService = cryptoFactory.getFreshKeySigningService(
            memberId = request.context.memberId
        )
        val ctor2 = type.constructors.firstOrNull {
            it.parameterCount == 2 &&
                    it.parameterTypes[0] == FreshKeySigningService::class.java &&
                    it.parameterTypes[1] == CipherSchemeMetadata::class.java
        }
        if (ctor2 != null) {
            return ctor2.newInstance(freshKeysService, cryptoFactory.cipherSchemeMetadata) as CryptoRpcHandler<Any, Any>
        }
        val ctor1 = type.constructors.first {
            it.parameterCount == 1 &&
                    it.parameterTypes[0] == FreshKeySigningService::class.java
        }
        return ctor1.newInstance(freshKeysService) as CryptoRpcHandler<Any, Any>
    }

    private class WireFreshKeysFreshKeyHandler(
        private val freshKeysService: FreshKeySigningService,
        private val cipherSchemeMetadata: CipherSchemeMetadata
    ) : CryptoRpcHandler<WireRequestContext, WireFreshKeysFreshKey> {
        override fun handle(context: WireRequestContext, request: WireFreshKeysFreshKey): Any {
            val publicKey = if(request.externalId.isNullOrBlank()) {
                freshKeysService.freshKey(request.context.items.toMap())
            } else {
                freshKeysService.freshKey(UUID.fromString(request.externalId), request.context.items.toMap())
            }
            return WirePublicKey(ByteBuffer.wrap(cipherSchemeMetadata.encodeAsByteArray(publicKey)))
        }
    }

    private class WireFreshKeysEnsureWrappingKeyHandler(
        private val freshKeysService: FreshKeySigningService
    ) : CryptoRpcHandler<WireRequestContext, WireFreshKeysEnsureWrappingKey> {
        override fun handle(context: WireRequestContext, request: WireFreshKeysEnsureWrappingKey): Any {
            freshKeysService.ensureWrappingKey()
            return WireNoContentValue()
        }
    }

    private class WireFreshKeysSignHandler(
        private val freshKeysService: FreshKeySigningService,
        private val cipherSchemeMetadata: CipherSchemeMetadata
    ) : CryptoRpcHandler<WireRequestContext, WireFreshKeysSign> {
        override fun handle(context: WireRequestContext, request: WireFreshKeysSign): Any {
            val publicKey = cipherSchemeMetadata.decodePublicKey(request.publicKey.array())
            val signature = freshKeysService.sign(publicKey, request.bytes.array(), request.context.items.toMap())
            return WireSignatureWithKey(
                ByteBuffer.wrap(cipherSchemeMetadata.encodeAsByteArray(signature.by)),
                ByteBuffer.wrap(signature.bytes)
            )
        }
    }

    private class WireFreshKeysSignWithSpecHandler(
        private val freshKeysService: FreshKeySigningService,
        private val cipherSchemeMetadata: CipherSchemeMetadata
    ) : CryptoRpcHandler<WireRequestContext, WireFreshKeysSignWithSpec> {
        override fun handle(context: WireRequestContext, request: WireFreshKeysSignWithSpec): Any {
            val publicKey = cipherSchemeMetadata.decodePublicKey(request.publicKey.array())
            val spec = SignatureSpec(
                signatureName = request.signatureSpec.signatureName,
                customDigestName = if (request.signatureSpec.customDigestName.isNullOrBlank()) {
                    null
                } else {
                    DigestAlgorithmName(request.signatureSpec.customDigestName)
                }
            )
            val signature = freshKeysService.sign(publicKey, spec, request.bytes.array(), request.context.items.toMap())
            return WireSignatureWithKey(
                ByteBuffer.wrap(cipherSchemeMetadata.encodeAsByteArray(signature.by)),
                ByteBuffer.wrap(signature.bytes)
            )
        }
    }

    private class WireFreshKeysFilterMyKeysHandler(
        private val freshKeysService: FreshKeySigningService,
        private val cipherSchemeMetadata: CipherSchemeMetadata
    ) : CryptoRpcHandler<WireRequestContext, WireFreshKeysFilterMyKeys> {
        override fun handle(context: WireRequestContext, request: WireFreshKeysFilterMyKeys): Any {
            val candidates = request.keys.map {
                cipherSchemeMetadata.decodePublicKey(it.array())
            }
            val found = freshKeysService.filterMyKeys(candidates)
            return WirePublicKeys(
                found.map {
                    ByteBuffer.wrap(cipherSchemeMetadata.encodeAsByteArray(it))
                }
            )
        }
    }
}