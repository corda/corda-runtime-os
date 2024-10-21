package net.corda.crypto.service.impl.rpc

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.config.impl.RetryingConfig
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.impl.retrying.CryptoRetryingExecutor
import net.corda.crypto.impl.toMap
import net.corda.crypto.impl.toSignatureSpec
import net.corda.crypto.service.CryptoExceptionCategorizer
import net.corda.crypto.service.CryptoExceptionType
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
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import net.corda.crypto.service.impl.toCryptoSigningKey

@Suppress("LongParameterList")
class CryptoFlowOpsProcessor(
    private val cryptoService: CryptoService,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    config: RetryingConfig,
    private val keyEncodingService: KeyEncodingService,
    private val cryptoExceptionCategorizer: CryptoExceptionCategorizer
) : SyncRPCProcessor<FlowOpsRequest, FlowEvent> {

    override val requestClass = FlowOpsRequest::class.java
    override val responseClass = FlowEvent::class.java

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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

            try {
                val successResponse = executor.executeWithRetry { handleRequest(requestPayload, request.context) }
                createSuccessResponse(request, successResponse)
            } catch (e: Exception) {
                processException(e, request)
            }.also {
                CordaMetrics.Metric.Crypto.FlowOpsProcessorExecutionTime.builder()
                    .withTag(CordaMetrics.Tag.OperationName, requestPayload::class.java.simpleName)
                    .build()
                    .record(Duration.ofNanos(System.nanoTime() - startTime))
            }
        }

        return result.value as FlowEvent
    }

    private fun processException(e: Exception, request: FlowOpsRequest): Record<String, FlowEvent> {
        val requestId = request.flowExternalEventContext.requestId

        return when (cryptoExceptionCategorizer.categorize(e)) {
            CryptoExceptionType.TRANSIENT -> throw CordaHTTPServerTransientException(requestId, e)
            else -> {
                logger.warn("Failed to handle ${request.request::class.java.name} for tenant ${request.context.tenantId}", e)
                externalEventResponseFactory.platformError(request.flowExternalEventContext, e)
            }
        }
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
