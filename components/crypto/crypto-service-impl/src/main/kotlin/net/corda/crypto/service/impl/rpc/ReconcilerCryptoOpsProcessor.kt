package net.corda.crypto.service.impl.rpc

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.config.impl.RetryingConfig
import net.corda.crypto.core.CryptoService
import net.corda.crypto.impl.retrying.CryptoRetryingExecutor
import net.corda.crypto.service.impl.bus.CryptoOpsBusProcessor.Companion.avroSecureHashesToDto
import net.corda.crypto.service.impl.bus.CryptoOpsBusProcessor.Companion.avroShortHashesToDto
import net.corda.data.ExceptionEnvelope
import net.corda.data.crypto.SecureHashes
import net.corda.data.crypto.ShortHashes
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.data.crypto.wire.ops.reconciliation.request.LookUpKeyById
import net.corda.data.crypto.wire.ops.reconciliation.response.LookupKeyByIdError
import net.corda.data.crypto.wire.ops.reconciliation.response.LookupKeyByIdResponse
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.utilities.trace
import org.slf4j.LoggerFactory

class ReconcilerCryptoOpsProcessor(
    private val cryptoService: CryptoService,
    config: RetryingConfig,
    private val keyEncodingService: KeyEncodingService,
) : SyncRPCProcessor<LookUpKeyById, LookupKeyByIdResponse> {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val requestClass = LookUpKeyById::class.java
    override val responseClass = LookupKeyByIdResponse::class.java
    private val executor = CryptoRetryingExecutor(logger, config.maxAttempts.toLong(), config.waitBetweenMills)

    override fun process(request: LookUpKeyById): LookupKeyByIdResponse {
        logger.trace { "Processing request: ${request::class.java.name}" }

        return try {
            executor.executeWithRetry {
                when (val avroKeyIds = request.keyIds) {
                    is ShortHashes -> {
                        val keyInfos = cryptoService.lookupSigningKeysByPublicKeyShortHash(
                            request.tenantId,
                            avroShortHashesToDto(avroKeyIds)
                        )
                        LookupKeyByIdResponse(CryptoSigningKeys(keyInfos.map { it.toCryptoSigningKey(keyEncodingService) }))
                    }
                    is SecureHashes -> {
                        val keyInfos = cryptoService.lookupSigningKeysByPublicKeyHashes(
                            request.tenantId,
                            avroSecureHashesToDto(avroKeyIds)
                        )
                        LookupKeyByIdResponse(CryptoSigningKeys(keyInfos.map { it.toCryptoSigningKey(keyEncodingService) }))
                    }
                    else -> {
                        throw IllegalArgumentException("Unexpected type for ${avroKeyIds::class.java.name}.")
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to handle $requestClass for tenant ${request.tenantId}", e)
            LookupKeyByIdResponse(LookupKeyByIdError(ExceptionEnvelope(e::class.java.name, e.message)))
        }
    }

}