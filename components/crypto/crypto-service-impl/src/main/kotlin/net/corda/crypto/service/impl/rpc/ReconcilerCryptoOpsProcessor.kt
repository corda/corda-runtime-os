package net.corda.crypto.service.impl.rpc

import java.nio.ByteBuffer
import net.corda.crypto.config.impl.RetryingConfig
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.impl.retrying.CryptoRetryingExecutor
import net.corda.crypto.service.impl.bus.CryptoOpsBusProcessor.Companion.avroSecureHashesToDto
import net.corda.crypto.service.impl.bus.CryptoOpsBusProcessor.Companion.avroShortHashesToDto
import net.corda.data.ExceptionEnvelope
import net.corda.data.crypto.SecureHashes
import net.corda.data.crypto.ShortHashes
import net.corda.data.crypto.wire.ops.encryption.response.EncryptionOpsError
import net.corda.data.crypto.wire.ops.encryption.response.EncryptionOpsResponse
import net.corda.data.crypto.wire.ops.reconciliation.LookUpKeyById
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.utilities.trace
import org.slf4j.LoggerFactory

class ReconcilerCryptoOpsProcessor(
    private val cryptoService: CryptoService,
    config: RetryingConfig
) : SyncRPCProcessor<LookUpKeyById, EncryptionOpsResponse> {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val requestClass = LookUpKeyById::class.java
    override val responseClass = EncryptionOpsResponse::class.java
    private val executor = CryptoRetryingExecutor(logger, config.maxAttempts.toLong(), config.waitBetweenMills)

    override fun process(request: LookUpKeyById): EncryptionOpsResponse {
        logger.trace { "Processing request: ${request::class.java.name}" }

        return try {
            executor.executeWithRetry {
                when (val avroKeyIds = request.keyIds) {
                    is ShortHashes -> {
                        val keyInfos = cryptoService.lookupSigningKeysByPublicKeyShortHash(
                            request.tenantId,
                            avroShortHashesToDto(avroKeyIds)
                        )
                        EncryptionOpsResponse(keyInfos.map { it.publicKey })
                    }
                    is SecureHashes -> {
                        val keyInfos = cryptoService.lookupSigningKeysByPublicKeyHashes(
                            request.tenantId,
                            avroSecureHashesToDto(avroKeyIds)
                        )
                        EncryptionOpsResponse(keyInfos.map { it.publicKey })
                    }
                    else -> {
                        throw IllegalArgumentException("Unexpected type for ${avroKeyIds::class.java.name}.")
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to handle $requestClass for tenant ${request.tenantId}", e)
            EncryptionOpsResponse(EncryptionOpsError(ExceptionEnvelope(e::class.java.name, e.message)))
        }
    }

}