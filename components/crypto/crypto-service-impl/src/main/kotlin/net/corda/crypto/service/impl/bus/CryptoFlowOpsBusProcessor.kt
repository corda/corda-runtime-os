package net.corda.crypto.service.impl.bus

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.crypto.client.CryptoOpsProxyClient
import net.corda.crypto.config.impl.flowBusProcessor
import net.corda.crypto.config.impl.toCryptoConfig
import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.crypto.impl.retrying.BackoffStrategy
import net.corda.crypto.impl.retrying.CryptoRetryingExecutor
import net.corda.data.ExceptionEnvelope
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.SecureHashes
import net.corda.data.crypto.ShortHashes
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.crypto.wire.ops.flow.commands.SignFlowCommand
import net.corda.data.crypto.wire.ops.flow.queries.ByIdsFlowQuery
import net.corda.data.crypto.wire.ops.flow.queries.FilterMyKeysFlowQuery
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.debug
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.ShortHash
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.reflect.jvm.javaField

class CryptoFlowOpsBusProcessor(
    private val cryptoOpsClient: CryptoOpsProxyClient,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    event: ConfigChangedEvent
) : DurableProcessor<String, FlowOpsRequest> {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private fun avroShortHashesToStrings(shortHashes: ShortHashes): List<String> =
            shortHashes.hashes.map {
                ShortHash.of(it).value
            }

        private fun avroSecureHashesToStrings(secureHashes: SecureHashes): List<String> =
            secureHashes.hashes.map {
                SecureHash(it.algorithm, it.bytes.array()).toString()
            }
    }

    override val keyClass: Class<String> = String::class.java
    override val valueClass = FlowOpsRequest::class.java

    private val config = event.config.toCryptoConfig().flowBusProcessor()

    private val executor = CryptoRetryingExecutor(
        logger,
        BackoffStrategy.createBackoff(config.maxAttempts, config.waitBetweenMills)
    )

    override fun onNext(events: List<Record<String, FlowOpsRequest>>): List<Record<*, *>> =
        events.mapNotNull { onNext(it) }

    private fun onNext(event: Record<String, FlowOpsRequest>): Record<*, *>? {
        val request = event.value
        if (request == null) {
            logger.error("Unexpected null payload for event with the key={} in topic={}", event.key, event.topic)
            return null // cannot send any error back as have no idea where to send to
        }

        val requestId = request.flowExternalEventContext.requestId
        val flowId = request.flowExternalEventContext.flowId
        val expireAt = getRequestExpireAt(request)
        
        if (Instant.now() >= expireAt) {
            logger.error(
                "Event ${request.request::class.java} for tenant ${request.context.tenantId} is no longer valid, " +
                        "expired at $expireAt { requestId: $requestId, key: $flowId }"
            )
            return externalEventResponseFactory.transientError(
                request.flowExternalEventContext,
                ExceptionEnvelope("Expired", "Expired at $expireAt")
            )
        }
        return try {
            logger.info(
                "Handling ${request.request::class.java.name} for tenant ${request.context.tenantId} " +
                        "{ requestId: $requestId, key: $flowId }"
            )
            val response = executor.executeWithRetry {
                handleRequest(request.request, request.context)
            }
            if (Instant.now() >= expireAt) {
                logger.error(
                    "Event ${request.request::class.java} for tenant ${request.context.tenantId} is no longer valid, " +
                            "expired at $expireAt { requestId: $requestId, key: $flowId }"
                )
                return externalEventResponseFactory.transientError(
                    request.flowExternalEventContext,
                    ExceptionEnvelope("Expired", "Expired at $expireAt")
                )
            }
            val result = externalEventResponseFactory.success(
                request.flowExternalEventContext,
                FlowOpsResponse(createResponseContext(request), response, null)
            )
            logger.debug {
                "Handled ${request.request::class.java.name} for tenant ${request.context.tenantId} " +
                        "{ requestId: $requestId, key: $flowId }"
            }
            result
        } catch (t: Throwable) {
            logger.error(
                "Failed to handle ${request.request::class.java} for tenant ${request.context.tenantId} " +
                        "{ requestId: $requestId, key: $flowId }",
                t
            )
            return externalEventResponseFactory.platformError(request.flowExternalEventContext, t)
        }
    }

    private fun handleRequest(request: Any, context: CryptoRequestContext): Any {
        return when (request) {
            is FilterMyKeysFlowQuery ->
                cryptoOpsClient.filterMyKeysProxy(
                    tenantId = context.tenantId,
                    candidateKeys = request.keys
                )
            is SignFlowCommand ->
                cryptoOpsClient.signProxy(
                    tenantId = context.tenantId,
                    publicKey = request.publicKey,
                    signatureSpec = request.signatureSpec,
                    data = request.bytes,
                    context = request.context
                )
            is ByIdsFlowQuery -> {
                val keyIds = when (val avroKeyIds = request.keyIds) {
                    is ShortHashes -> avroShortHashesToStrings(avroKeyIds)
                    is SecureHashes -> avroSecureHashesToStrings(avroKeyIds)
                    else -> throw IllegalArgumentException("Unexpected type for ${request::keyIds.javaField}: ${avroKeyIds::class.java.name}")
                }

                cryptoOpsClient.lookUpForKeysByIdsProxy(
                    tenantId = context.tenantId,
                    candidateKeys = keyIds
                )
            }
            else ->
                throw IllegalArgumentException("Unknown request type ${request::class.java.name}")
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