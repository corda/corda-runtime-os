package net.corda.crypto.service.rpc.ops

import net.corda.crypto.service.SigningService
import net.corda.crypto.service.SigningServiceFactory
import net.corda.crypto.service.rpc.CryptoRpcHandler
import net.corda.crypto.service.rpc.toMap
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoPublicKeys
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.CryptoSignature
import net.corda.data.crypto.wire.CryptoSignatureSchemes
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.ops.rpc.FilterMyKeysRpcQuery
import net.corda.data.crypto.wire.ops.rpc.GenerateFreshKeyRpcCommand
import net.corda.data.crypto.wire.ops.rpc.GenerateKeyPairCommand
import net.corda.data.crypto.wire.ops.rpc.PublicKeyRpcQuery
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.data.crypto.wire.ops.rpc.SignRpcCommand
import net.corda.data.crypto.wire.ops.rpc.SignWithAliasRpcCommand
import net.corda.data.crypto.wire.ops.rpc.SignWithAliasSpecRpcCommand
import net.corda.data.crypto.wire.ops.rpc.SignWithSpecRpcCommand
import net.corda.data.crypto.wire.ops.rpc.SupportedSchemesRpcQuery
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.slf4j.Logger
import java.lang.reflect.Constructor
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class CryptoOpsRpcProcessor(
    private val signingFactory: SigningServiceFactory
) : RPCResponderProcessor<RpcOpsRequest, RpcOpsResponse> {

    companion object {
        private val logger: Logger = contextLogger()

        private val handlers = mapOf<Class<*>, Class<out CryptoRpcHandler<out Any>>>(
            SupportedSchemesRpcQuery::class.java to SupportedSchemesRpcQueryHandler::class.java,
            PublicKeyRpcQuery::class.java to PublicKeyRpcQueryHandler::class.java,
            FilterMyKeysRpcQuery::class.java to FilterMyKeysRpcQueryHandler::class.java,
            GenerateKeyPairCommand::class.java to GenerateKeyPairCommandHandler::class.java,
            GenerateFreshKeyRpcCommand::class.java to GenerateFreshKeyRpcCommandHandler::class.java,
            SignRpcCommand::class.java to SignRpcCommandHandler::class.java,
            SignWithSpecRpcCommand::class.java to SignWithSpecRpcCommandHandler::class.java,
            SignWithAliasRpcCommand::class.java to SignWithAliasRpcCommandHandler::class.java,
            SignWithAliasSpecRpcCommand::class.java to SignWithAliasSpecRpcCommandHandler::class.java,
            // findHSMKey
            // findHSMKey
            // findHSM
        )

        private val constructors = ConcurrentHashMap<Class<*>, ConstructorHolder>()
    }

    override fun onNext(request: RpcOpsRequest, respFuture: CompletableFuture<RpcOpsResponse>) {
        try {
            logger.info("Handling {} for tenant {}", request.request::class.java.name, request.context.tenantId)
            val handler = getHandler(request)
            val response = handler.handle(request.context, request.request)
            val result = RpcOpsResponse(createResponseContext(request), response)
            logger.debug(
                "Handled {} for tenant {} with {}",
                request.request::class.java.name,
                request.context.tenantId,
                if (result.response != null) result.response::class.java.name else "null"
            )
            respFuture.complete(result)
        } catch (e: Throwable) {
            val message = "Failed to handle ${request.request::class.java} for tenant ${request.context.tenantId}"
            logger.error(message, e)
            respFuture.completeExceptionally(CryptoServiceLibraryException(message, e))
        }
    }

    private fun createResponseContext(request: RpcOpsRequest) = CryptoResponseContext(
        request.context.requestingComponent,
        request.context.requestTimestamp,
        request.context.requestId,
        Instant.now(),
        request.context.tenantId,
        request.context.other
    )

    @Suppress("UNCHECKED_CAST")
    private fun getHandler(request: RpcOpsRequest): CryptoRpcHandler<Any> {
        val signingService = signingFactory.getInstance(
            tenantId = request.context.tenantId
        )
        val holder = constructors.computeIfAbsent(request.request::class.java) {
            val type = handlers[request.request::class.java] ?: throw CryptoServiceBadRequestException(
                "Unknown request type ${request.request::class.java.name}"
            )
            ConstructorHolder(
                handler = type,
                constructor = type.constructors.firstOrNull {
                    it.parameterCount == 1 && it.parameterTypes[0] == SigningService::class.java
                }
            )
        }
        if (holder.constructor == null) {
            throw NotImplementedError(
                "The handler ${holder.handler.name} " +
                        "doesn't have constructor accepting only ${SigningService::class.java.name}"
            )
        }
        return holder.constructor.newInstance(signingService) as CryptoRpcHandler<Any>
    }

    private class SupportedSchemesRpcQueryHandler(
        private val signingService: SigningService
    ) : CryptoRpcHandler<SupportedSchemesRpcQuery> {
        override fun handle(context: CryptoRequestContext, request: SupportedSchemesRpcQuery): Any {
            return CryptoSignatureSchemes(
                signingService.getSupportedSchemes(request.category)
            )
        }
    }

    private class PublicKeyRpcQueryHandler(
        private val signingService: SigningService
    ) : CryptoRpcHandler<PublicKeyRpcQuery> {
        override fun handle(context: CryptoRequestContext, request: PublicKeyRpcQuery): Any {
            val publicKey = signingService.findPublicKey(request.alias)
            return if (publicKey != null) {
                CryptoPublicKey(ByteBuffer.wrap(signingService.schemeMetadata.encodeAsByteArray(publicKey)))
            } else {
                CryptoNoContentValue()
            }
        }
    }

    private class FilterMyKeysRpcQueryHandler(
        private val signingService: SigningService
    ) : CryptoRpcHandler<FilterMyKeysRpcQuery> {
        override fun handle(context: CryptoRequestContext, request: FilterMyKeysRpcQuery): Any {
            val candidates = request.keys.map {
                signingService.schemeMetadata.decodePublicKey(it.array())
            }
            val found = signingService.filterMyKeys(candidates)
            return CryptoPublicKeys(
                found.map {
                    ByteBuffer.wrap(signingService.schemeMetadata.encodeAsByteArray(it))
                }
            )
        }
    }

    private class GenerateKeyPairCommandHandler(
        private val signingService: SigningService
    ) : CryptoRpcHandler<GenerateKeyPairCommand> {
        override fun handle(context: CryptoRequestContext, request: GenerateKeyPairCommand): Any {
            val publicKey = signingService.generateKeyPair(
                category = request.category,
                alias = request.alias,
                context = request.context.items.toMap()
            )
            return CryptoPublicKey(ByteBuffer.wrap(signingService.schemeMetadata.encodeAsByteArray(publicKey)))
        }
    }

    private class GenerateFreshKeyRpcCommandHandler(
        private val signingService: SigningService
    ) : CryptoRpcHandler<GenerateFreshKeyRpcCommand> {
        override fun handle(context: CryptoRequestContext, request: GenerateFreshKeyRpcCommand): Any {
            val publicKey = if (request.externalId.isNullOrBlank()) {
                signingService.freshKey(
                    request.context.items.toMap()
                )
            } else {
                signingService.freshKey(
                    UUID.fromString(request.externalId),
                    request.context.items.toMap()
                )
            }
            return CryptoPublicKey(
                ByteBuffer.wrap(signingService.schemeMetadata.encodeAsByteArray(publicKey))
            )
        }
    }

    private class SignRpcCommandHandler(
        private val signingService: SigningService
    ) : CryptoRpcHandler<SignRpcCommand> {
        override fun handle(context: CryptoRequestContext, request: SignRpcCommand): Any {
            val publicKey = signingService.schemeMetadata.decodePublicKey(request.publicKey.array())
            val signature = signingService.sign(
                publicKey,
                request.bytes.array(),
                request.context.items.toMap()
            )
            return CryptoSignatureWithKey(
                ByteBuffer.wrap(signingService.schemeMetadata.encodeAsByteArray(signature.by)),
                ByteBuffer.wrap(signature.bytes)
            )
        }
    }

    private class SignWithSpecRpcCommandHandler(
        private val signingService: SigningService
    ) : CryptoRpcHandler<SignWithSpecRpcCommand> {
        override fun handle(context: CryptoRequestContext, request: SignWithSpecRpcCommand): Any {
            val publicKey = signingService.schemeMetadata.decodePublicKey(request.publicKey.array())
            val spec = SignatureSpec(
                signatureName = request.signatureSpec.signatureName,
                customDigestName = if (request.signatureSpec.customDigestName.isNullOrBlank()) {
                    null
                } else {
                    DigestAlgorithmName(request.signatureSpec.customDigestName)
                }
            )
            val signature = signingService.sign(publicKey, spec, request.bytes.array(), request.context.items.toMap())
            return CryptoSignatureWithKey(
                ByteBuffer.wrap(signingService.schemeMetadata.encodeAsByteArray(signature.by)),
                ByteBuffer.wrap(signature.bytes)
            )
        }
    }

    private class SignWithAliasRpcCommandHandler(
        private val signingService: SigningService
    ) : CryptoRpcHandler<SignWithAliasRpcCommand> {
        override fun handle(context: CryptoRequestContext, request: SignWithAliasRpcCommand): Any {
            val signature = signingService.sign(
                request.alias,
                request.bytes.array(),
                request.context.items.toMap()
            )
            return CryptoSignature(ByteBuffer.wrap(signature))
        }
    }

    private class SignWithAliasSpecRpcCommandHandler(
        private val signingService: SigningService
    ) : CryptoRpcHandler<SignWithAliasSpecRpcCommand> {
        override fun handle(context: CryptoRequestContext, request: SignWithAliasSpecRpcCommand): Any {
            val spec = SignatureSpec(
                signatureName = request.signatureSpec.signatureName,
                customDigestName = if (request.signatureSpec.customDigestName.isNullOrBlank()) {
                    null
                } else {
                    DigestAlgorithmName(request.signatureSpec.customDigestName)
                }
            )
            val signature = signingService.sign(
                request.alias,
                spec,
                request.bytes.array(),
                request.context.items.toMap()
            )
            return CryptoSignature(ByteBuffer.wrap(signature))
        }
    }

    private class ConstructorHolder(
        val handler: Class<*>,
        val constructor: Constructor<*>?
    )
}