package net.corda.crypto.service.impl.rpc

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.config.impl.RetryingConfig
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.isRecoverable
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.impl.retrying.CryptoRetryingExecutor
import net.corda.crypto.impl.toMap
import net.corda.crypto.impl.toSignatureSpec
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.crypto.wire.ops.flow.commands.SignFlowCommand
import net.corda.data.crypto.wire.ops.flow.queries.ByIdsFlowQuery
import net.corda.data.crypto.wire.ops.flow.queries.FilterMyKeysFlowQuery
import net.corda.data.flow.event.FlowEvent
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.messaging.api.exception.CordaHTTPServerTransientException
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.messaging.api.records.Record
import net.corda.metrics.CordaMetrics
import net.corda.utilities.MDC_CLIENT_ID
import net.corda.utilities.MDC_EXTERNAL_EVENT_ID
import net.corda.utilities.MDC_FLOW_ID
import net.corda.utilities.debug
import net.corda.utilities.trace
import net.corda.utilities.translateFlowContextToMDC
import net.corda.utilities.withMDC
import org.bouncycastle.crypto.CryptoException
import org.hibernate.exception.JDBCConnectionException
import org.hibernate.exception.LockAcquisitionException
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.sql.SQLTransientException
import java.time.Duration
import java.time.Instant
import javax.persistence.LockTimeoutException
import javax.persistence.OptimisticLockException
import javax.persistence.PersistenceException
import javax.persistence.QueryTimeoutException

@Suppress("LongParameterList")
class CryptoFlowOpsProcessor(
    private val cryptoService: CryptoService,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    config: RetryingConfig,
    private val keyEncodingService: KeyEncodingService
) : SyncRPCProcessor<FlowOpsRequest, FlowEvent> {

    override val requestClass = FlowOpsRequest::class.java
    override val responseClass = FlowEvent::class.java

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private val transientDBExceptions = setOf(
            SQLTransientException::class.java,
            JDBCConnectionException::class.java,
            LockAcquisitionException::class.java,
            LockTimeoutException::class.java,
            QueryTimeoutException::class.java,
            OptimisticLockException::class.java
        )
    }

    private val executor = CryptoRetryingExecutor(logger, config.maxAttempts.toLong(), config.waitBetweenMills)

    override fun process(request: FlowOpsRequest): FlowEvent {
        logger.trace { "Processing request: ${request::class.java.name}" }

        val clientRequestId = request.flowExternalEventContext.contextProperties.toMap()[MDC_CLIENT_ID] ?: ""

        val mdc = mapOf(
            MDC_FLOW_ID to request.flowExternalEventContext.flowId,
            MDC_CLIENT_ID to clientRequestId,
            MDC_EXTERNAL_EVENT_ID to request.flowExternalEventContext.requestId
        ) + translateFlowContextToMDC(request.flowExternalEventContext.contextProperties.toMap())

        val result = withMDC(mdc) {
            val requestPayload = request.request
            val startTime = System.nanoTime()
            logger.debug { "Handling ${requestPayload::class.java.name} for tenant ${request.context.tenantId}" }

            val response = try {
                executor.executeWithRetry { handleRequest(requestPayload, request.context) }
            } catch (e: Exception) {
                processException(e, request, requestPayload)
            }

            createSuccessResponse(request, response).also {
                CordaMetrics.Metric.Crypto.FlowOpsProcessorExecutionTime.builder()
                    .withTag(CordaMetrics.Tag.OperationName, requestPayload::class.java.simpleName)
                    .build()
                    .record(Duration.ofNanos(System.nanoTime() - startTime))
            }
        }


        return result.value as FlowEvent
    }

    private fun processException(e: Exception, request: FlowOpsRequest, requestPayload: Any) {
        if (isTransientException(e)) {
            throw CordaHTTPServerTransientException(request.flowExternalEventContext.requestId, e)
        } else {
            handlePlatformException(e, request, requestPayload)
        }
    }

    private fun isTransientException(e: Exception) : Boolean {
        return e::class.java in transientDBExceptions ||
                (e is PersistenceException && e.cause is SQLTransientException) ||
                (e is CryptoException && e.isRecoverable())
    }

    private fun handlePlatformException(e: Exception, request: FlowOpsRequest, requestPayload: Any): Record<String, FlowEvent> {
        logger.warn("Failed to handle ${requestPayload::class.java.name} for tenant ${request.context.tenantId}", e)
        return externalEventResponseFactory.platformError(request.flowExternalEventContext, e)
    }

    private fun createSuccessResponse(request: FlowOpsRequest, response: Any) =
        externalEventResponseFactory.success(
            request.flowExternalEventContext,
            FlowOpsResponse(createResponseContext(request), response, null)
        )

    private fun handleRequest(request: Any, context: CryptoRequestContext): Any {
        return when (request) {
            is FilterMyKeysFlowQuery -> {
                val keys = request.keys.map { ShortHash.of(publicKeyIdFromBytes(it.array())) }
                cryptoService.lookupSigningKeysByPublicKeyShortHash(context.tenantId, keys)
            }

            is SignFlowCommand -> {
                val publicKey = cryptoService.schemeMetadata.decodePublicKey(request.publicKey.array())
                val signature = cryptoService.sign(
                    context.tenantId,
                    publicKey,
                    request.signatureSpec.toSignatureSpec(cryptoService.schemeMetadata),
                    request.bytes.array(),
                    request.context.toMap()
                )
                CryptoSignatureWithKey(
                    ByteBuffer.wrap(cryptoService.schemeMetadata.encodeAsByteArray(signature.by)),
                    ByteBuffer.wrap(signature.bytes)
                )
            }

            is ByIdsFlowQuery ->
                CryptoSigningKeys(cryptoService.lookupSigningKeysByPublicKeyHashes(
                    context.tenantId,
                    request.fullKeyIds.hashes.map { SecureHashImpl(it.algorithm, it.bytes.array()) }
                ).map { it.toCryptoSigningKey(keyEncodingService) })

            else -> throw IllegalArgumentException("Unknown request type ${request::class.java.name}")
        }
    }

    private fun createResponseContext(request: FlowOpsRequest) = CryptoResponseContext(
        request.context.requestingComponent,
        request.context.requestTimestamp,
        request.context.requestId,
        Instant.now(),
        request.context.tenantId,
        KeyValuePairList(request.context.other.items.toList())
    )
}
