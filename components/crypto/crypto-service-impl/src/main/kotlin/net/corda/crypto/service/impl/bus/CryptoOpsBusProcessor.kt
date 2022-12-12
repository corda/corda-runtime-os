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
import net.corda.crypto.service.impl.CryptoRequestHandler
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
import net.corda.v5.base.util.uncheckedCast
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

        private val handlersFactories =
            mapOf<Class<*>, (signingService: SigningService) -> CryptoRequestHandler<*, out Any>>(
                DeriveSharedSecretCommand::class.java to { DeriveSharedSecretCommandHandler(it) },
                GenerateFreshKeyRpcCommand::class.java to { GenerateFreshKeyRpcCommandHandler(it) },
                GenerateKeyPairCommand::class.java to { GenerateKeyPairCommandHandler(it) },
                GenerateWrappingKeyRpcCommand::class.java to { GenerateWrappingKeyRpcCommandHandler(it) },
                ByIdsRpcQuery::class.java to { ByIdsRpcQueryHandler(it) },
                KeysRpcQuery::class.java to { KeysRpcQueryHandler(it) },
                SupportedSchemesRpcQuery::class.java to { SupportedSchemesRpcQueryHandler(it) },
                SignRpcCommand::class.java to { SignRpcCommandHandler(it) },
            )

        private fun Map<Class<*>, (signingService: SigningService) -> CryptoRequestHandler<*, out Any>>.getHandlerForRequest(
            requestType: Class<*>,
            signingService: SigningService
        ): CryptoRequestHandler<Any, out Any> {
            val handlerFactory =
                this[requestType] ?: throw IllegalArgumentException("Unknown request type ${requestType.name}")
            return uncheckedCast(handlerFactory(signingService))
        }
    }

    private val config = event.config.toCryptoConfig().opsBusProcessor()

    private val executor = CryptoRetryingExecutor(
        logger,
        BackoffStrategy.createBackoff(config.maxAttempts, config.waitBetweenMills)
    )

    override fun onNext(request: RpcOpsRequest, respFuture: CompletableFuture<RpcOpsResponse>) {
        try {
            logger.info("Handling {} for tenant {}", request.request::class.java.name, request.context.tenantId)
            val signingService = signingFactory.getInstance()
            val requestType = request.request::class.java
            val handler = handlersFactories.getHandlerForRequest(requestType, signingService)
            val response = executor.executeWithRetry {
                handler.handle(request.request, request.context)
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
    ) : CryptoRequestHandler<SupportedSchemesRpcQuery, CryptoKeySchemes> {
        override val requestClass = SupportedSchemesRpcQuery::class.java

        override fun handle(request: SupportedSchemesRpcQuery, context: CryptoRequestContext): CryptoKeySchemes {
            return CryptoKeySchemes(
                signingService.getSupportedSchemes(context.tenantId, request.category)
            )
        }
    }

    private class ByIdsRpcQueryHandler(
        private val signingService: SigningService
    ) : CryptoRequestHandler<ByIdsRpcQuery, CryptoSigningKeys> {
        override val requestClass = ByIdsRpcQuery::class.java

        override fun handle(request: ByIdsRpcQuery, context: CryptoRequestContext): CryptoSigningKeys {
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
    ) : CryptoRequestHandler<KeysRpcQuery, CryptoSigningKeys> {
        override val requestClass = KeysRpcQuery::class.java

        override fun handle(request: KeysRpcQuery, context: CryptoRequestContext): CryptoSigningKeys {
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
    ) : CryptoRequestHandler<DeriveSharedSecretCommand, CryptoDerivedSharedSecret> {
        override val requestClass = DeriveSharedSecretCommand::class.java

        override fun handle(request: DeriveSharedSecretCommand, context: CryptoRequestContext): CryptoDerivedSharedSecret {
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
    ) : CryptoRequestHandler<GenerateKeyPairCommand, CryptoPublicKey> {
        override val requestClass = GenerateKeyPairCommand::class.java

        override fun handle(request: GenerateKeyPairCommand, context: CryptoRequestContext): CryptoPublicKey {
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
    ) : CryptoRequestHandler<GenerateFreshKeyRpcCommand, CryptoPublicKey> {
        override val requestClass = GenerateFreshKeyRpcCommand::class.java

        override fun handle(request: GenerateFreshKeyRpcCommand, context: CryptoRequestContext): CryptoPublicKey {
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
    ) : CryptoRequestHandler<GenerateWrappingKeyRpcCommand, CryptoNoContentValue> {
        override val requestClass = GenerateWrappingKeyRpcCommand::class.java

        override fun handle(request: GenerateWrappingKeyRpcCommand, context: CryptoRequestContext): CryptoNoContentValue {
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
    ) : CryptoRequestHandler<SignRpcCommand, CryptoSignatureWithKey> {
        override val requestClass = SignRpcCommand::class.java

        override fun handle(request: SignRpcCommand, context: CryptoRequestContext): CryptoSignatureWithKey {
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