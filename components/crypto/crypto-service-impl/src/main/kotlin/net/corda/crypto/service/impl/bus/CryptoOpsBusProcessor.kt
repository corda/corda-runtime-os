package net.corda.crypto.service.impl.bus

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.crypto.config.impl.opsBusProcessor
import net.corda.crypto.config.impl.toCryptoConfig
import net.corda.crypto.impl.retrying.BackoffStrategy
import net.corda.crypto.impl.retrying.CryptoRetryingExecutor
import net.corda.crypto.impl.toMap
import net.corda.crypto.impl.toSignatureSpec
import net.corda.crypto.impl.toWire
import net.corda.crypto.service.KeyOrderBy
import net.corda.crypto.service.SigningService
import net.corda.crypto.service.SigningServiceFactory
import net.corda.crypto.service.impl.CryptoRequestsHandlers
import net.corda.crypto.service.impl.CryptoRequestsHandlers.Handler
import net.corda.data.crypto.wire.CryptoDerivedSharedSecret
import net.corda.data.crypto.wire.CryptoKeySchemes
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.data.crypto.wire.ops.rpc.commands.DeriveSharedSecretCommand
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateFreshKeyRpcCommand
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateKeyPairCommand
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateWrappingKeyRpcCommand
import net.corda.data.crypto.wire.ops.rpc.commands.SignRpcCommand
import net.corda.data.crypto.wire.ops.rpc.queries.ByIdsRpcQuery
import net.corda.data.crypto.wire.ops.rpc.queries.KeysRpcQuery
import net.corda.data.crypto.wire.ops.rpc.queries.SupportedSchemesRpcQuery
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.slf4j.Logger
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CompletableFuture

class CryptoOpsBusProcessor(
    private val signingFactory: SigningServiceFactory,
    event: ConfigChangedEvent
) : RPCResponderProcessor<RpcOpsRequest, RpcOpsResponse> {
    companion object {
        private val logger: Logger = contextLogger()
        private val handlers = mapOf<Class<*>, Class<out Handler<out Any>>>(
            DeriveSharedSecretCommand::class.java to DeriveSharedSecretCommandHandler::class.java,
            GenerateFreshKeyRpcCommand::class.java to GenerateFreshKeyRpcCommandHandler::class.java,
            GenerateKeyPairCommand::class.java to GenerateKeyPairCommandHandler::class.java,
            GenerateWrappingKeyRpcCommand::class.java to GenerateWrappingKeyRpcCommandHandler::class.java,
            ByIdsRpcQuery::class.java to ByIdsRpcQueryHandler::class.java,
            KeysRpcQuery::class.java to KeysRpcQueryHandler::class.java,
            SupportedSchemesRpcQuery::class.java to SupportedSchemesRpcQueryHandler::class.java,
            SignRpcCommand::class.java to SignRpcCommandHandler::class.java,
        )
    }

    private val config = event.config.toCryptoConfig().opsBusProcessor()

    private val executor = CryptoRetryingExecutor(
        logger,
        BackoffStrategy.createBackoff(config.maxAttempts, config.waitBetweenMills)
    )

    private val cryptoRequestsHandlers = CryptoRequestsHandlers(handlers)

    override fun onNext(request: RpcOpsRequest, respFuture: CompletableFuture<RpcOpsResponse>) {
        try {
            logger.info("Handling {} for tenant {}", request.request::class.java.name, request.context.tenantId)
            val signingService = signingFactory.getInstance()
            val handler = cryptoRequestsHandlers.getHandler(request.request::class.java, signingService)
            val response = executor.executeWithRetry {
                handler.handle(request.context, request.request)
            }
            val result = RpcOpsResponse(createResponseContext(request), response)
            logger.debug {
                "Handled ${request.request::class.java.name} for tenant ${request.context.tenantId} with" +
                        " ${if (result.response != null) result.response::class.java.name else "null"}"
            }
            respFuture.complete(result)
        } catch (e: Throwable) {
            logger.error("Failed to handle ${request.request::class.java} for tenant ${request.context.tenantId}", e)
            respFuture.completeExceptionally(e)
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
            return CryptoKeySchemes(
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
                filter = request.filter.toMap()
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

    private class DeriveSharedSecretCommandHandler(
        private val signingService: SigningService
    ) : Handler<DeriveSharedSecretCommand> {
        override fun handle(context: CryptoRequestContext, request: DeriveSharedSecretCommand): Any {
            val sharedSecret = signingService.deriveSharedSecret(
                tenantId = context.tenantId,
                publicKey = signingService.schemeMetadata.decodePublicKey(request.publicKey.array()),
                otherPublicKey = signingService.schemeMetadata.decodePublicKey(request.otherPublicKey.array()),
                context = request.context.items.toMap()
            )
            return CryptoDerivedSharedSecret(ByteBuffer.wrap(sharedSecret))
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
                    scheme = signingService.schemeMetadata.findKeyScheme(request.schemeCodeName),
                    context = request.context.items.toMap()
                )
            } else {
                signingService.generateKeyPair(
                    tenantId = context.tenantId,
                    category = request.category,
                    alias = request.alias,
                    externalId = request.externalId,
                    scheme = signingService.schemeMetadata.findKeyScheme(request.schemeCodeName),
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
                    tenantId = context.tenantId,
                    category = request.category,
                    scheme = signingService.schemeMetadata.findKeyScheme(request.schemeCodeName),
                    context = request.context.items.toMap()
                )
            } else {
                signingService.freshKey(
                    tenantId = context.tenantId,
                    category = request.category,
                    externalId = request.externalId,
                    scheme = signingService.schemeMetadata.findKeyScheme(request.schemeCodeName),
                    context = request.context.items.toMap()
                )
            }
            return CryptoPublicKey(
                ByteBuffer.wrap(signingService.schemeMetadata.encodeAsByteArray(publicKey))
            )
        }
    }

    private class GenerateWrappingKeyRpcCommandHandler(
        private val signingService: SigningService
    ) : Handler<GenerateWrappingKeyRpcCommand> {
        override fun handle(context: CryptoRequestContext, request: GenerateWrappingKeyRpcCommand): Any {
            signingService.createWrappingKey(
                hsmId = request.hsmId,
                failIfExists = request.failIfExists,
                masterKeyAlias = request.masterKeyAlias,
                context = request.context.items.toMap()
            )
            return CryptoNoContentValue()
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
                request.signatureSpec.toSignatureSpec(signingService.schemeMetadata),
                request.bytes.array(),
                request.context.toMap()
            )
            return CryptoSignatureWithKey(
                ByteBuffer.wrap(signingService.schemeMetadata.encodeAsByteArray(signature.by)),
                ByteBuffer.wrap(signature.bytes),
                signature.context.toWire()
            )
        }
    }
}