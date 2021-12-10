package net.corda.crypto.service.rpc

import net.corda.crypto.service.CryptoFactory
import net.corda.crypto.SigningService
import net.corda.data.crypto.wire.WireNoContentValue
import net.corda.data.crypto.wire.WirePublicKey
import net.corda.data.crypto.wire.WireRequestContext
import net.corda.data.crypto.wire.WireResponseContext
import net.corda.data.crypto.wire.WireSignature
import net.corda.data.crypto.wire.WireSignatureSchemes
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
import java.util.concurrent.CompletableFuture

class SigningServiceRpcProcessor(
    private val cryptoFactory: CryptoFactory
) : RPCResponderProcessor<WireSigningRequest, WireSigningResponse> {

    companion object {
        const val CATEGORY = "category"

        private val logger: Logger = contextLogger()

        private val handlers = mapOf<Class<*>, Class<out CryptoRpcHandler<*, *>>>(
            WireSigningFindPublicKey::class.java to WireSigningFindPublicKeyHandler::class.java,
            WireSigningGenerateKeyPair::class.java to WireSigningGenerateKeyPairHandler::class.java,
            WireSigningSign::class.java to WireSigningSignHandler::class.java,
            WireSigningSignWithSpec::class.java to WireSigningSignWithSpecHandler::class.java,
            WireSigningSignWithAlias::class.java to WireSigningSignWithAliasHandler::class.java,
            WireSigningSignWithAliasSpec::class.java to WireSigningSignWithAliasSpecHandler::class.java,
            WireSigningGetSupportedSchemes::class.java to WireSigningGetSupportedSchemesHandler::class.java
        )
    }

    override fun onNext(request: WireSigningRequest, respFuture: CompletableFuture<WireSigningResponse>) {
        try {
            logger.info("Handling {} for member {}", request.request::class.java.name, request.context.memberId)
            val result = WireSigningResponse(
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
            val message = "Failed to handle ${request.request::class.java} for member ${request.context.memberId}"
            logger.error(message, e)
            respFuture.completeExceptionally(CryptoServiceLibraryException(message, e))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getHandler(request: WireSigningRequest): CryptoRpcHandler<Any, Any> {
        val type = handlers[request.request::class.java] ?: throw CryptoServiceBadRequestException(
            "Unknown request type ${request.request::class.java.name}"
        )
        val signingService = cryptoFactory.getSigningService(
            memberId = request.context.memberId,
            category = request.context.other.items.firstOrNull { it.key == CATEGORY }?.value
                ?: throw CryptoServiceBadRequestException(
                    "The category was not specified for request type ${request.request::class.java.name}"
                )
        )
        val ctor2 = type.constructors.firstOrNull {
            it.parameterCount == 2 &&
                    it.parameterTypes[0] == SigningService::class.java &&
                    it.parameterTypes[1] == CipherSchemeMetadata::class.java
        }
        if (ctor2 != null) {
            return ctor2.newInstance(signingService, cryptoFactory.cipherSchemeMetadata) as CryptoRpcHandler<Any, Any>
        }
        val ctor1 = type.constructors.first {
            it.parameterCount == 1 &&
                    it.parameterTypes[0] == SigningService::class.java
        }
        return ctor1.newInstance(signingService) as CryptoRpcHandler<Any, Any>
    }

    private class WireSigningFindPublicKeyHandler(
        private val signingService: SigningService,
        private val cipherSchemeMetadata: CipherSchemeMetadata
    ) : CryptoRpcHandler<WireRequestContext, WireSigningFindPublicKey> {
        override fun handle(context: WireRequestContext, request: WireSigningFindPublicKey): Any {
            val publicKey = signingService.findPublicKey(request.alias)
            return if (publicKey != null) {
                WirePublicKey(ByteBuffer.wrap(cipherSchemeMetadata.encodeAsByteArray(publicKey)))
            } else {
                WireNoContentValue()
            }
        }
    }

    private class WireSigningGenerateKeyPairHandler(
        private val signingService: SigningService,
        private val cipherSchemeMetadata: CipherSchemeMetadata
    ) : CryptoRpcHandler<WireRequestContext, WireSigningGenerateKeyPair> {
        override fun handle(context: WireRequestContext, request: WireSigningGenerateKeyPair): Any {
            val publicKey = signingService.generateKeyPair(request.alias, request.context.items.toMap())
            return WirePublicKey(ByteBuffer.wrap(cipherSchemeMetadata.encodeAsByteArray(publicKey)))
        }
    }

    private class WireSigningSignHandler(
        private val signingService: SigningService,
        private val cipherSchemeMetadata: CipherSchemeMetadata
    ) : CryptoRpcHandler<WireRequestContext, WireSigningSign> {
        override fun handle(context: WireRequestContext, request: WireSigningSign): Any {
            val publicKey = cipherSchemeMetadata.decodePublicKey(request.publicKey.array())
            val signature = signingService.sign(publicKey, request.bytes.array(), request.context.items.toMap())
            return WireSignatureWithKey(
                ByteBuffer.wrap(cipherSchemeMetadata.encodeAsByteArray(signature.by)),
                ByteBuffer.wrap(signature.bytes)
            )
        }
    }

    private class WireSigningSignWithSpecHandler(
        private val signingService: SigningService,
        private val cipherSchemeMetadata: CipherSchemeMetadata
    ) : CryptoRpcHandler<WireRequestContext, WireSigningSignWithSpec> {
        override fun handle(context: WireRequestContext, request: WireSigningSignWithSpec): Any {
            val publicKey = cipherSchemeMetadata.decodePublicKey(request.publicKey.array())
            val spec = SignatureSpec(
                signatureName = request.signatureSpec.signatureName,
                customDigestName = if (request.signatureSpec.customDigestName.isNullOrBlank()) {
                    null
                } else {
                    DigestAlgorithmName(request.signatureSpec.customDigestName)
                }
            )
            val signature = signingService.sign(publicKey, spec, request.bytes.array(), request.context.items.toMap())
            return WireSignatureWithKey(
                ByteBuffer.wrap(cipherSchemeMetadata.encodeAsByteArray(signature.by)),
                ByteBuffer.wrap(signature.bytes)
            )
        }
    }

    private class WireSigningSignWithAliasHandler(
        private val signingService: SigningService
    ) : CryptoRpcHandler<WireRequestContext, WireSigningSignWithAlias> {
        override fun handle(context: WireRequestContext, request: WireSigningSignWithAlias): Any {
            val signature = signingService.sign(request.alias, request.bytes.array(), request.context.items.toMap())
            return WireSignature(ByteBuffer.wrap(signature))
        }
    }

    private class WireSigningSignWithAliasSpecHandler(
        private val signingService: SigningService
    ) : CryptoRpcHandler<WireRequestContext, WireSigningSignWithAliasSpec> {
        override fun handle(context: WireRequestContext, request: WireSigningSignWithAliasSpec): Any {
            val spec = SignatureSpec(
                signatureName = request.signatureSpec.signatureName,
                customDigestName = if (request.signatureSpec.customDigestName.isNullOrBlank()) {
                    null
                } else {
                    DigestAlgorithmName(request.signatureSpec.customDigestName)
                }
            )
            val signature = signingService.sign(request.alias, spec, request.bytes.array(), request.context.items.toMap())
            return WireSignature(ByteBuffer.wrap(signature))
        }
    }

    private class WireSigningGetSupportedSchemesHandler(
        private val signingService: SigningService
    ) : CryptoRpcHandler<WireRequestContext, WireSigningGetSupportedSchemes> {
        override fun handle(context: WireRequestContext, request: WireSigningGetSupportedSchemes): Any {
            return WireSignatureSchemes(signingService.supportedSchemes.map { it.codeName })
        }
    }
}