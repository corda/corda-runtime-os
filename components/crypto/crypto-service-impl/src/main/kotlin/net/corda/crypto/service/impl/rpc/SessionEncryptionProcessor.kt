package net.corda.crypto.service.impl.rpc

import net.corda.crypto.config.impl.RetryingConfig
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.impl.retrying.CryptoRetryingExecutor
import net.corda.data.ExceptionEnvelope
import net.corda.data.crypto.wire.ops.encryption.request.EncryptRpcCommand
import net.corda.data.crypto.wire.ops.encryption.response.CryptoEncryptionResult
import net.corda.data.crypto.wire.ops.encryption.response.EncryptionOpsError
import net.corda.data.crypto.wire.ops.encryption.response.EncryptionOpsResponse
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

class SessionEncryptionProcessor(
    private val cryptoService: CryptoService,
    config: RetryingConfig,
) : SyncRPCProcessor<EncryptRpcCommand, EncryptionOpsResponse> {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val requestClass = EncryptRpcCommand::class.java
    override val responseClass = EncryptionOpsResponse::class.java
    private val executor = CryptoRetryingExecutor(logger, config.maxAttempts.toLong(), config.waitBetweenMills)

    override fun process(request: EncryptRpcCommand): EncryptionOpsResponse {
        logger.trace { "Processing request: ${request::class.java.name}" }

        return try {
            executor.executeWithRetry {
                val cipherBytes = cryptoService.encrypt(
                    CryptoTenants.P2P,
                    request.plainBytes.array(),
                    request.alias,
                )
                EncryptionOpsResponse(CryptoEncryptionResult(ByteBuffer.wrap(cipherBytes)))
            }
        } catch (e: Exception) {
            logger.warn("Failed to handle $requestClass for tenant ${CryptoTenants.P2P}", e)
            EncryptionOpsResponse(EncryptionOpsError(ExceptionEnvelope(e::class.java.name, e.message)))
        }
    }
}
