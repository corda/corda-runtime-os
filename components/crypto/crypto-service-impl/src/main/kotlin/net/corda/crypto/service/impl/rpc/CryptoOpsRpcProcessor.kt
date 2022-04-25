package net.corda.crypto.service.impl.rpc

import net.corda.crypto.service.KeyOrderBy
import net.corda.crypto.service.SigningService
import net.corda.crypto.service.SigningServiceFactory
import net.corda.crypto.service.impl.WireProcessor
import net.corda.crypto.service.impl.toMap
import net.corda.crypto.service.impl.toSignatureSpec
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.CryptoSignatureSchemes
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateFreshKeyRpcCommand
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateKeyPairCommand
import net.corda.data.crypto.wire.ops.rpc.commands.SignRpcCommand
import net.corda.data.crypto.wire.ops.rpc.commands.SignWithSpecRpcCommand
import net.corda.data.crypto.wire.ops.rpc.queries.ByIdsRpcQuery
import net.corda.data.crypto.wire.ops.rpc.queries.KeysRpcQuery
import net.corda.data.crypto.wire.ops.rpc.queries.SupportedSchemesRpcQuery
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.slf4j.Logger
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CompletableFuture

class CryptoOpsRpcProcessor(
    private val signingFactory: SigningServiceFactory
) : WireProcessor(handlers), RPCResponderProcessor<RpcOpsRequest, RpcOpsResponse> {
    companion object {
        private val logger: Logger = contextLogger()
        private val handlers = mapOf<Class<*>, Class<out Handler<out Any>>>(
            GenerateFreshKeyRpcCommand::class.java to GenerateFreshKeyRpcCommandHandler::class.java,
            GenerateKeyPairCommand::class.java to GenerateKeyPairCommandHandler::class.java,
            SignWithSpecRpcCommand::class.java to SignWithSpecRpcCommandHandler::class.java,
            ByIdsRpcQuery::class.java to ByIdsRpcQueryHandler::class.java,
            KeysRpcQuery::class.java to KeysRpcQueryHandler::class.java,
            SupportedSchemesRpcQuery::class.java to SupportedSchemesRpcQueryHandler::class.java,
            SignRpcCommand::class.java to SignRpcCommandHandler::class.java,
            // findHSM
        )
    }

    override fun onNext(request: RpcOpsRequest, respFuture: CompletableFuture<RpcOpsResponse>) {
        try {
            logger.info("Handling {} for tenant {}", request.request::class.java.name, request.context.tenantId)
            val signingService = signingFactory.getInstance()
            val response = getHandler(request.request::class.java, signingService)
                .handle(request.context, request.request)
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

    private class SupportedSchemesRpcQueryHandler(
        private val signingService: SigningService
    ) : Handler<SupportedSchemesRpcQuery> {
        override fun handle(context: CryptoRequestContext, request: SupportedSchemesRpcQuery): Any {
            return CryptoSignatureSchemes(
                signingService.getSupportedSchemes(context.tenantId, request.category)
            )
        }
    }

    private class ByIdsRpcQueryHandler(
        private val signingService: SigningService
    ) : Handler<ByIdsRpcQuery> {
        override fun handle(context: CryptoRequestContext, request: ByIdsRpcQuery): Any {
            val found = signingService.lookup(context.tenantId, request.keys)
            return CryptoSigningKeys(
                found.map {
                    CryptoSigningKey(
                        it.id,
                        it.tenantId,
                        it.category,
                        it.alias,
                        it.hsmAlias,
                        ByteBuffer.wrap(it.publicKey),
                        it.schemeCodeName,
                        it.masterKeyAlias,
                        it.encodingVersion,
                        it.externalId,
                        it.created
                    )
                }
            )
        }
    }

    private class KeysRpcQueryHandler(
        private val signingService: SigningService
    ) : Handler<KeysRpcQuery> {
        override fun handle(context: CryptoRequestContext, request: KeysRpcQuery): Any {
            val found = signingService.lookup(
                tenantId = context.tenantId,
                skip = request.skip,
                take = request.take,
                orderBy = KeyOrderBy.valueOf(request.orderBy.name),
                filter = request.filter.items.toMap()
            )
            return CryptoSigningKeys(
                found.map {
                    CryptoSigningKey(
                        it.id,
                        it.tenantId,
                        it.category,
                        it.alias,
                        it.hsmAlias,
                        ByteBuffer.wrap(it.publicKey),
                        it.schemeCodeName,
                        it.masterKeyAlias,
                        it.encodingVersion,
                        it.externalId,
                        it.created
                    )
                }
            )
        }
    }

    private class GenerateKeyPairCommandHandler(
        private val signingService: SigningService
    ) : Handler<GenerateKeyPairCommand> {
        override fun handle(context: CryptoRequestContext, request: GenerateKeyPairCommand): Any {
            val publicKey = if (request.externalId.isNullOrBlank()) {
                signingService.generateKeyPair(
                    tenantId = context.tenantId,
                    category = request.category,
                    alias = request.alias,
                    context = request.context.items.toMap()
                )
            } else {
                signingService.generateKeyPair(
                    tenantId = context.tenantId,
                    category = request.category,
                    alias = request.alias,
                    externalId = request.externalId,
                    context = request.context.items.toMap()
                )
            }
            return CryptoPublicKey(ByteBuffer.wrap(signingService.schemeMetadata.encodeAsByteArray(publicKey)))
        }
    }

    private class GenerateFreshKeyRpcCommandHandler(
        private val signingService: SigningService
    ) : Handler<GenerateFreshKeyRpcCommand> {
        override fun handle(context: CryptoRequestContext, request: GenerateFreshKeyRpcCommand): Any {
            val publicKey = if (request.externalId.isNullOrBlank()) {
                signingService.freshKey(
                    context.tenantId,
                    request.context.items.toMap()
                )
            } else {
                signingService.freshKey(
                    context.tenantId,
                    request.externalId,
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
    ) : Handler<SignRpcCommand> {
        override fun handle(context: CryptoRequestContext, request: SignRpcCommand): Any {
            val publicKey = signingService.schemeMetadata.decodePublicKey(request.publicKey.array())
            val signature = signingService.sign(
                context.tenantId,
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
    ) : Handler<SignWithSpecRpcCommand> {
        override fun handle(context: CryptoRequestContext, request: SignWithSpecRpcCommand): Any {
            val publicKey = signingService.schemeMetadata.decodePublicKey(request.publicKey.array())
            val spec = request.signatureSpec.toSignatureSpec(signingService.schemeMetadata)
            val signature = signingService.sign(
                context.tenantId,
                publicKey,
                spec,
                request.bytes.array(),
                request.context.items.toMap()
            )
            return CryptoSignatureWithKey(
                ByteBuffer.wrap(signingService.schemeMetadata.encodeAsByteArray(signature.by)),
                ByteBuffer.wrap(signature.bytes)
            )
        }
    }
}