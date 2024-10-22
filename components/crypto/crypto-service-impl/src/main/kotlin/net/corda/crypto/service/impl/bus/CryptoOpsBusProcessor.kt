package net.corda.crypto.service.impl.bus

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.config.impl.RetryingConfig
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.InvalidParamsException
import net.corda.crypto.core.KeyAlreadyExistsException
import net.corda.crypto.core.KeyOrderBy
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.ShortHash
import net.corda.crypto.impl.retrying.CryptoRetryingExecutor
import net.corda.crypto.impl.utils.toMap
import net.corda.crypto.impl.utils.toSignatureSpec
import net.corda.data.crypto.SecureHashes
import net.corda.data.crypto.ShortHashes
import net.corda.data.crypto.wire.CryptoDerivedSharedSecret
import net.corda.data.crypto.wire.CryptoKeySchemes
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.CryptoSignatureWithKey
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
import net.corda.metrics.CordaMetrics
import net.corda.utilities.debug
import net.corda.v5.crypto.SecureHash
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CompletableFuture
import net.corda.crypto.service.impl.toCryptoSigningKey

@Suppress("LongParameterList")
class CryptoOpsBusProcessor(
    private val cryptoService: CryptoService,
    config: RetryingConfig,
    private val keyEncodingService: KeyEncodingService
) :
    RPCResponderProcessor<RpcOpsRequest, RpcOpsResponse> {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)


        private fun avroShortHashesToDto(shortHashes: ShortHashes): List<ShortHash> =
            shortHashes.hashes.map {
                ShortHash.of(it)
            }

        private fun avroSecureHashesToDto(secureHashes: SecureHashes): List<SecureHash> =
            secureHashes.hashes.map {
                SecureHashImpl(it.algorithm, it.bytes.array())
            }
    }

    private val executor = CryptoRetryingExecutor(logger, config.maxAttempts.toLong(), config.waitBetweenMills)

    override fun onNext(request: RpcOpsRequest, respFuture: CompletableFuture<RpcOpsResponse>) {
        try {
            logger.debug { "Handling ${request.request::class.java.name} for tenant ${request.context.tenantId}" }
            val response = executor.executeWithRetry {
                handleRequest(request.request, request.context)
            }
            val result = RpcOpsResponse(createResponseContext(request), response)
            logger.debug {
                "Handled ${request.request::class.java.name} for tenant ${request.context.tenantId} with" +
                        " ${if (result.response != null) result.response::class.java.name else "null"}"
            }
            respFuture.complete(result)
        } catch (e: KeyAlreadyExistsException) {
            logger.warn("Key alias ${e.alias} already exists in tenant ${e.tenantId}; returning error rather than recreating")
            respFuture.completeExceptionally(e)
        } catch (e: Throwable) {
            logger.warn("Failed to handle ${request.request::class.java} for tenant ${request.context.tenantId}", e)
            respFuture.completeExceptionally(e)
        }
    }

    @Suppress("ComplexMethod")
    private fun handleRequest(request: Any, context: CryptoRequestContext): Any {

        fun handleSupportedSchemesRpcQuery(): CryptoKeySchemes =
            CryptoKeySchemes(cryptoService.supportedSchemes.map { it.key.codeName })

        fun handleByIdsRpcQuery(request: ByIdsRpcQuery): CryptoSigningKeys {
            val foundKeys =
                when (val avroKeyIds = request.keyIds) {
                    is ShortHashes -> {
                        cryptoService.lookupSigningKeysByPublicKeyShortHash(
                            context.tenantId,
                            avroShortHashesToDto(avroKeyIds)
                        )
                    }
                    is SecureHashes -> {
                        cryptoService.lookupSigningKeysByPublicKeyHashes(
                            context.tenantId,
                            avroSecureHashesToDto(avroKeyIds)
                        )
                    }
                    else -> throw IllegalArgumentException("Unexpected type for ${avroKeyIds::class.java.name}")
                }

            return CryptoSigningKeys(foundKeys.map { it.toCryptoSigningKey(keyEncodingService) })
        }

        fun handleKeysRpcQuery(request: KeysRpcQuery): CryptoSigningKeys {
            val found = cryptoService.querySigningKeys(
                tenantId = context.tenantId,
                skip = request.skip,
                take = request.take,
                orderBy = KeyOrderBy.valueOf(request.orderBy.name),
                filter = request.filter.toMap()
            )
            return CryptoSigningKeys(found.map { it.toCryptoSigningKey(keyEncodingService) })
        }

        fun handleDeriveSharedSecretCommand(request: DeriveSharedSecretCommand): CryptoDerivedSharedSecret {
            val sharedSecret = cryptoService.deriveSharedSecret(
                tenantId = context.tenantId,
                publicKey = cryptoService.schemeMetadata.decodePublicKey(request.publicKey.array()),
                otherPublicKey = cryptoService.schemeMetadata.decodePublicKey(request.otherPublicKey.array()),
                context = request.context.items.toMap()
            )
            return CryptoDerivedSharedSecret(ByteBuffer.wrap(sharedSecret))
        }

        fun handleGenerateKeyPairCommand(request: GenerateKeyPairCommand): CryptoPublicKey {
            val keyPair = if (request.externalId.isNullOrBlank()) {
                cryptoService.generateKeyPair(
                    tenantId = context.tenantId,
                    category = request.category,
                    alias = request.alias,
                    scheme = cryptoService.schemeMetadata.findKeySchemeOrThrow(request.schemeCodeName),
                    context = request.context.items.toMap()
                )
            } else {
                cryptoService.generateKeyPair(
                    tenantId = context.tenantId,
                    category = request.category,
                    alias = request.alias,
                    externalId = request.externalId,
                    scheme = cryptoService.schemeMetadata.findKeySchemeOrThrow(request.schemeCodeName),
                    context = request.context.items.toMap()
                )
            }
            return CryptoPublicKey(ByteBuffer.wrap(cryptoService.schemeMetadata.encodeAsByteArray(keyPair.publicKey)))
        }

        fun handleGenerateFreshKeyRpcCommand(request: GenerateFreshKeyRpcCommand): CryptoPublicKey =
            cryptoService.generateKeyPair(
                tenantId = context.tenantId,
                category = request.category,
                alias = null,
                externalId = request.externalId,
                scheme = cryptoService.schemeMetadata.findKeySchemeOrThrow(request.schemeCodeName),
                context = request.context.items.toMap()
            ).let { CryptoPublicKey(ByteBuffer.wrap(cryptoService.schemeMetadata.encodeAsByteArray(it.publicKey))) }

        fun handleGenerateWrappingKeyRpcCommand(request: GenerateWrappingKeyRpcCommand): CryptoNoContentValue {
            cryptoService.createWrappingKey(
                hsmId = request.hsmId,
                failIfExists = request.failIfExists,
                masterKeyAlias = request.masterKeyAlias,
                context = request.context.items.toMap()
            )
            return CryptoNoContentValue()
        }

        fun handleSignRpcCommand(request: SignRpcCommand): CryptoSignatureWithKey {
            val publicKey = cryptoService.schemeMetadata.decodePublicKey(request.publicKey.array())
            val signature = cryptoService.sign(
                context.tenantId,
                publicKey,
                request.signatureSpec.toSignatureSpec(cryptoService.schemeMetadata),
                request.bytes.array(),
                request.context.toMap()
            )
            return CryptoSignatureWithKey(
                ByteBuffer.wrap(cryptoService.schemeMetadata.encodeAsByteArray(signature.by)),
                ByteBuffer.wrap(signature.bytes)
            )
        }

        return CordaMetrics.Metric.Crypto.OpsProcessorExecutionTime.builder()
            .withTag(CordaMetrics.Tag.OperationName, request::class.java.simpleName)
            .build()
            .recordCallable<Any> {
                when (request) {
                    is SupportedSchemesRpcQuery -> handleSupportedSchemesRpcQuery()
                    is ByIdsRpcQuery -> handleByIdsRpcQuery(request)
                    is KeysRpcQuery -> handleKeysRpcQuery(request)
                    is DeriveSharedSecretCommand -> handleDeriveSharedSecretCommand(request)
                    is GenerateKeyPairCommand -> handleGenerateKeyPairCommand(request)
                    is GenerateFreshKeyRpcCommand -> handleGenerateFreshKeyRpcCommand(request)
                    is GenerateWrappingKeyRpcCommand -> handleGenerateWrappingKeyRpcCommand(request)
                    is SignRpcCommand -> handleSignRpcCommand(request)
                    else -> throw IllegalArgumentException("Unknown request type ${request::class.java.name}")
                }
            }!!
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
