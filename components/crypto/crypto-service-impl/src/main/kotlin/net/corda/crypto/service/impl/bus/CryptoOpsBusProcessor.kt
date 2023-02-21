package net.corda.crypto.service.impl.bus

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.config.impl.opsBusProcessor
import net.corda.crypto.config.impl.toCryptoConfig
import net.corda.crypto.core.InvalidParamsException
import net.corda.crypto.core.KeyAlreadyExistsException
import net.corda.crypto.impl.retrying.BackoffStrategy
import net.corda.crypto.impl.retrying.CryptoRetryingExecutor
import net.corda.crypto.impl.toMap
import net.corda.crypto.impl.toSignatureSpec
import net.corda.crypto.impl.toWire
import net.corda.crypto.service.KeyOrderBy
import net.corda.crypto.service.SigningKeyInfo
import net.corda.crypto.service.SigningService
import net.corda.crypto.service.SigningServiceFactory
import net.corda.data.crypto.SecureHashes
import net.corda.data.crypto.ShortHashes
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
import net.corda.v5.base.util.debug
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.ShortHash
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CompletableFuture

class CryptoOpsBusProcessor(
    private val signingFactory: SigningServiceFactory,
    event: ConfigChangedEvent
) : RPCResponderProcessor<RpcOpsRequest, RpcOpsResponse> {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private fun SigningKeyInfo.toAvro(): CryptoSigningKey =
            CryptoSigningKey(
                this.id,
                this.tenantId,
                this.category,
                this.alias,
                this.hsmAlias,
                ByteBuffer.wrap(this.publicKey),
                this.schemeCodeName,
                this.masterKeyAlias,
                this.encodingVersion,
                this.externalId,
                this.created
            )

        private fun avroShortHashesToDto(shortHashes: ShortHashes): List<ShortHash> =
            shortHashes.hashes.map {
                ShortHash.of(it)
            }

        private fun avroSecureHashesToDto(secureHashes: SecureHashes): List<SecureHash> =
            secureHashes.hashes.map {
                SecureHash(it.algorithm, it.bytes.array())
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
            val response = executor.executeWithRetry {
                handleRequest(request.request, request.context, signingService)
            }
            val result = RpcOpsResponse(createResponseContext(request), response)
            logger.debug {
                "Handled ${request.request::class.java.name} for tenant ${request.context.tenantId} with" +
                        " ${if (result.response != null) result.response::class.java.name else "null"}"
            }
            respFuture.complete(result)
        } catch (e: KeyAlreadyExistsException) {
            logger.info("Key alias ${e.alias} already exists in tenant ${e.tenantId}; returning error rather than recreating")
            respFuture.completeExceptionally(e)
        } catch (e: Throwable) {
            logger.error("Failed to handle ${request.request::class.java} for tenant ${request.context.tenantId}", e)
            respFuture.completeExceptionally(e)
        }
    }

    @Suppress("ComplexMethod")
    private fun handleRequest(request: Any, context: CryptoRequestContext, signingService: SigningService): Any {

        fun handleSupportedSchemesRpcQuery(request: SupportedSchemesRpcQuery): CryptoKeySchemes {
            return CryptoKeySchemes(
                signingService.getSupportedSchemes(
                    context.tenantId,
                    request.category
                )
            )
        }

        fun handleByIdsRpcQuery(request: ByIdsRpcQuery): CryptoSigningKeys {
            val foundKeys =
                when (val avroKeyIds = request.keyIds) {
                    is ShortHashes -> {
                        signingService.lookupByIds(
                            context.tenantId,
                            avroShortHashesToDto(avroKeyIds)
                        )
                    }
                    is SecureHashes -> {
                        signingService.lookupByFullIds(
                            context.tenantId,
                            avroSecureHashesToDto(avroKeyIds)
                        )
                    }
                    else -> throw IllegalArgumentException("Unexpected type for ${avroKeyIds::class.java.name}")
                }

            return CryptoSigningKeys(foundKeys.map { it.toAvro() })
        }

        fun handleKeysRpcQuery(request: KeysRpcQuery): CryptoSigningKeys {
            val found = signingService.lookup(
                tenantId = context.tenantId,
                skip = request.skip,
                take = request.take,
                orderBy = KeyOrderBy.valueOf(request.orderBy.name),
                filter = request.filter.toMap()
            )
            return CryptoSigningKeys(found.map { it.toAvro() })
        }

        fun handleDeriveSharedSecretCommand(request: DeriveSharedSecretCommand): CryptoDerivedSharedSecret {
            val sharedSecret = signingService.deriveSharedSecret(
                tenantId = context.tenantId,
                publicKey = signingService.schemeMetadata.decodePublicKey(request.publicKey.array()),
                otherPublicKey = signingService.schemeMetadata.decodePublicKey(request.otherPublicKey.array()),
                context = request.context.items.toMap()
            )
            return CryptoDerivedSharedSecret(ByteBuffer.wrap(sharedSecret))
        }

        fun handleGenerateKeyPairCommand(request: GenerateKeyPairCommand): CryptoPublicKey {
            val publicKey = if (request.externalId.isNullOrBlank()) {
                signingService.generateKeyPair(
                    tenantId = context.tenantId,
                    category = request.category,
                    alias = request.alias,
                    scheme = signingService.schemeMetadata.findKeySchemeOrThrow(request.schemeCodeName),
                    context = request.context.items.toMap()
                )
            } else {
                signingService.generateKeyPair(
                    tenantId = context.tenantId,
                    category = request.category,
                    alias = request.alias,
                    externalId = request.externalId,
                    scheme = signingService.schemeMetadata.findKeySchemeOrThrow(request.schemeCodeName),
                    context = request.context.items.toMap()
                )
            }
            return CryptoPublicKey(ByteBuffer.wrap(signingService.schemeMetadata.encodeAsByteArray(publicKey)))
        }

        fun handleGenerateFreshKeyRpcCommand(request: GenerateFreshKeyRpcCommand): CryptoPublicKey {
            val publicKey = if (request.externalId.isNullOrBlank()) {
                signingService.freshKey(
                    tenantId = context.tenantId,
                    category = request.category,
                    scheme = signingService.schemeMetadata.findKeySchemeOrThrow(request.schemeCodeName),
                    context = request.context.items.toMap()
                )
            } else {
                signingService.freshKey(
                    tenantId = context.tenantId,
                    category = request.category,
                    externalId = request.externalId,
                    scheme = signingService.schemeMetadata.findKeySchemeOrThrow(request.schemeCodeName),
                    context = request.context.items.toMap()
                )
            }
            return CryptoPublicKey(ByteBuffer.wrap(signingService.schemeMetadata.encodeAsByteArray(publicKey)))
        }

        fun handleGenerateWrappingKeyRpcCommand(request: GenerateWrappingKeyRpcCommand): CryptoNoContentValue {
            signingService.createWrappingKey(
                hsmId = request.hsmId,
                failIfExists = request.failIfExists,
                masterKeyAlias = request.masterKeyAlias,
                context = request.context.items.toMap()
            )
            return CryptoNoContentValue()
        }

        fun handleSignRpcCommand(request: SignRpcCommand): CryptoSignatureWithKey {
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

        return when (request) {
            is SupportedSchemesRpcQuery -> handleSupportedSchemesRpcQuery(request)
            is ByIdsRpcQuery -> handleByIdsRpcQuery(request)
            is KeysRpcQuery -> handleKeysRpcQuery(request)
            is DeriveSharedSecretCommand -> handleDeriveSharedSecretCommand(request)
            is GenerateKeyPairCommand -> handleGenerateKeyPairCommand(request)
            is GenerateFreshKeyRpcCommand -> handleGenerateFreshKeyRpcCommand(request)
            is GenerateWrappingKeyRpcCommand -> handleGenerateWrappingKeyRpcCommand(request)
            is SignRpcCommand -> handleSignRpcCommand(request)
            else -> throw IllegalArgumentException("Unknown request type ${request::class.java.name}")
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

    private fun CipherSchemeMetadata.findKeySchemeOrThrow(codeName: String): KeyScheme {
        return try {
            this.findKeyScheme(codeName)
        } catch (exception: IllegalArgumentException) {
            throw exception.message?.let { InvalidParamsException(it) } ?: exception
        }
    }
}