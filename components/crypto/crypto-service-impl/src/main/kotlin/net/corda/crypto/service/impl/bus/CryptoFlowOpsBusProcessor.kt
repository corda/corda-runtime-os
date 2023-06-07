package net.corda.crypto.service.impl.bus

import net.corda.crypto.config.impl.RetryingConfig
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.crypto.impl.retrying.BackoffStrategy
import net.corda.crypto.impl.retrying.CryptoRetryingExecutor
import net.corda.crypto.impl.toMap
import net.corda.crypto.impl.toSignatureSpec
import net.corda.crypto.service.SigningService
import net.corda.data.ExceptionEnvelope
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
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.metrics.CordaMetrics
import net.corda.tracing.traceEventProcessingNullableSingle
import net.corda.utilities.MDC_CLIENT_ID
import net.corda.utilities.MDC_EXTERNAL_EVENT_ID
import net.corda.utilities.MDC_FLOW_ID
import net.corda.utilities.trace
import net.corda.utilities.translateFlowContextToMDC
import net.corda.utilities.withMDC
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant

@Suppress("Unused")
class CryptoFlowOpsBusProcessor(
    private val cryptoService: CryptoService,
    private val signingService: SigningService,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    config: RetryingConfig,
) : DurableProcessor<String, FlowOpsRequest> {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val keyClass: Class<String> = String::class.java
    override val valueClass = FlowOpsRequest::class.java

    private val executor = CryptoRetryingExecutor(
        logger,
        BackoffStrategy.createBackoff(config.maxAttempts, config.waitBetweenMills)
    )

    override fun onNext(events: List<Record<String, FlowOpsRequest>>): List<Record<*, *>> {
        logger.trace { "onNext processing messages ${events.joinToString(",") { it.key }}" }
        return events.mapNotNull { onNext(it) }
    }

    private fun onNext(event: Record<String, FlowOpsRequest>): Record<*, *>? {
        val request = event.value
        if (request == null) {
            logger.error("Unexpected null payload for event with the key={} in topic={}", event.key, event.topic)
            return null // cannot send any error back as have no idea where to send to
        }
        val eventType = request.request?.javaClass?.simpleName ?: "Unknown"
        return traceEventProcessingNullableSingle(event, "Crypto Event - $eventType") {
            val expireAt = getRequestExpireAt(request)
            val clientRequestId = request.flowExternalEventContext.contextProperties.toMap()[MDC_CLIENT_ID] ?: ""
            val mdc = mapOf(
                MDC_FLOW_ID to request.flowExternalEventContext.flowId,
                MDC_CLIENT_ID to clientRequestId,
                MDC_EXTERNAL_EVENT_ID to request.flowExternalEventContext.requestId
            ) + translateFlowContextToMDC(request.flowExternalEventContext.contextProperties.toMap())

            withMDC(mdc) {
                val requestPayload = request.request
                val startTime = System.nanoTime()
                logger.info("Handling ${requestPayload::class.java.name} for tenant ${request.context.tenantId}")

                try {
                    if (Instant.now() >= expireAt) {
                        logger.warn(
                            "Event ${requestPayload::class.java.name} for tenant ${request.context.tenantId} " +
                                    "is no longer valid, expired at $expireAt"
                        )
                        externalEventResponseFactory.transientError(
                            request.flowExternalEventContext,
                            ExceptionEnvelope("Expired", "Expired at $expireAt")
                        )
                    } else {
                        val response = executor.executeWithRetry {
                            handleRequest(requestPayload, request.context)
                        }

                        if (Instant.now() >= expireAt) {
                            logger.warn(
                                "Event ${requestPayload::class.java.name} for tenant ${request.context.tenantId} " +
                                        "is no longer valid, expired at $expireAt"
                            )
                            externalEventResponseFactory.transientError(
                                request.flowExternalEventContext,
                                ExceptionEnvelope("Expired", "Expired at $expireAt")
                            )
                        } else {
                            externalEventResponseFactory.success(
                                request.flowExternalEventContext,
                                FlowOpsResponse(createResponseContext(request), response, null)
                            )
                        }
                    }
                } catch (throwable: Throwable) {
                    logger.error(
                        "Failed to handle ${requestPayload::class.java.name} for tenant ${request.context.tenantId}",
                        throwable
                    )
                    externalEventResponseFactory.platformError(request.flowExternalEventContext, throwable)
                }.also {
                    CordaMetrics.Metric.Crypto.FlowOpsProcessorExecutionTime.builder()
                        .withTag(CordaMetrics.Tag.OperationName, requestPayload::class.java.simpleName)
                        .build()
                        .record(Duration.ofNanos(System.nanoTime() - startTime))
                }
            }
        }
    }

    private fun handleRequest(request: Any, context: CryptoRequestContext): Any {
        return when (request) {
            is FilterMyKeysFlowQuery -> {
                val keys = request.keys.map { ShortHash.of(publicKeyIdFromBytes(it.array())) }
                signingService.lookupSigningKeysByPublicKeyShortHash(context.tenantId, keys)
            }

            is SignFlowCommand -> {
                val publicKey = signingService.schemeMetadata.decodePublicKey(request.publicKey.array())
                val signature = signingService.sign(
                    context.tenantId,
                    publicKey,
                    request.signatureSpec.toSignatureSpec(signingService.schemeMetadata),
                    request.bytes.array(),
                    request.context.toMap()
                )
                CryptoSignatureWithKey(
                    ByteBuffer.wrap(signingService.schemeMetadata.encodeAsByteArray(signature.by)),
                    ByteBuffer.wrap(signature.bytes)
                )
            }

            is ByIdsFlowQuery ->
                CryptoSigningKeys(signingService.lookupSigningKeysByPublicKeyHashes(
                    context.tenantId,
                    request.fullKeyIds.hashes.map { SecureHashImpl(it.algorithm, it.bytes.array()) }
                ).map { it.toAvro() })

            else -> throw IllegalArgumentException("Unknown request type ${request::class.java.name}")
        }
    }

    private fun getRequestExpireAt(request: FlowOpsRequest): Instant =
        request.context.requestTimestamp.plusSeconds(getRequestValidityWindowSeconds(request))

    private fun getRequestValidityWindowSeconds(request: FlowOpsRequest): Long {
        return request.context.other.items.singleOrNull {
            it.key == CryptoFlowOpsTransformer.REQUEST_TTL_KEY
        }?.value?.toLong() ?: 300
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
