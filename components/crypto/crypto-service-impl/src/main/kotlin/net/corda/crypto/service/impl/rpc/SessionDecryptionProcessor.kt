package net.corda.crypto.service.impl.rpc

import net.corda.crypto.config.impl.RetryingConfig
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.impl.retrying.BackoffStrategy
import net.corda.crypto.impl.retrying.CryptoRetryingExecutor
import net.corda.crypto.impl.toMap
import net.corda.data.ExceptionEnvelope
import net.corda.data.crypto.wire.ops.encryption.request.DecryptRpcCommand
import net.corda.data.crypto.wire.ops.encryption.response.CryptoDecryptionResult
import net.corda.data.crypto.wire.ops.encryption.response.EncryptionOpsError
import net.corda.data.crypto.wire.ops.encryption.response.EncryptionOpsResponse
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

class SessionDecryptionProcessor(
    private val cryptoService: CryptoService,
    config: RetryingConfig,
)  : SyncRPCProcessor<DecryptRpcCommand, EncryptionOpsResponse> {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val executor = CryptoRetryingExecutor(
        logger,
        BackoffStrategy.createBackoff(config.maxAttempts, config.waitBetweenMills)
    )

    override val requestClass = DecryptRpcCommand::class.java
    override val responseClass = EncryptionOpsResponse::class.java

    override fun process(request: DecryptRpcCommand): EncryptionOpsResponse {
        logger.trace { "Processing request: ${request::class.java.name}" }

        return try {
            executor.executeWithRetry {
                val plainBytes = cryptoService.decrypt(
                    CryptoTenants.P2P,
                    request.cipherBytes.array(),
                    request.alias,
                    request.context.toMap(),
                )
                EncryptionOpsResponse(CryptoDecryptionResult(ByteBuffer.wrap(plainBytes)))
            }
        } catch (e: Exception) {
            logger.warn("Failed to handle $requestClass for tenant ${CryptoTenants.P2P}", e)
            EncryptionOpsResponse(EncryptionOpsError(ExceptionEnvelope(e::class.java.name, e.message)))
        }
    }
}
