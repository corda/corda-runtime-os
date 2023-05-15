package net.corda.crypto.service.impl.bus

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.crypto.client.CryptoOpsProxyClient
import net.corda.crypto.config.impl.flowBusProcessor
import net.corda.crypto.config.impl.toCryptoConfig
import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.crypto.impl.retrying.BackoffStrategy
import net.corda.crypto.impl.retrying.CryptoRetryingExecutor
import net.corda.crypto.impl.toMap
import net.corda.data.ExceptionEnvelope
import net.corda.data.KeyValuePairList
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
import net.corda.metrics.CordaMetrics
import net.corda.utilities.MDC_CLIENT_ID
import net.corda.utilities.MDC_EXTERNAL_EVENT_ID
import net.corda.utilities.MDC_FLOW_ID
import net.corda.utilities.trace
import net.corda.utilities.withMDC
import org.slf4j.LoggerFactory
import java.time.Instant

class CryptoFlowOpsBusProcessor(
    private val cryptoOpsClient: CryptoOpsProxyClient,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    event: ConfigChangedEvent
) : DurableProcessor<String, FlowOpsRequest> {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val keyClass: Class<String> = String::class.java
    override val valueClass = FlowOpsRequest::class.java

    private val config = event.config.toCryptoConfig().flowBusProcessor()

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

        val expireAt = getRequestExpireAt(request)
        val clientRequestId = request.flowExternalEventContext.contextProperties.toMap()[MDC_CLIENT_ID] ?: ""
        val mdc = mapOf(
            MDC_FLOW_ID to request.flowExternalEventContext.flowId,
            MDC_CLIENT_ID to clientRequestId,
            MDC_EXTERNAL_EVENT_ID to request.flowExternalEventContext.requestId
        )

        return withMDC(mdc) {
            logger.info("Handling ${request.request::class.java.name} for tenant ${request.context.tenantId}")

            try {
                if (Instant.now() >= expireAt) {
                    logger.warn("Event ${request.request::class.java.name} for tenant ${request.context.tenantId} " +
                            "is no longer valid, expired at $expireAt")
                    externalEventResponseFactory.transientError(
                        request.flowExternalEventContext,
                        ExceptionEnvelope("Expired", "Expired at $expireAt")
                    )
                } else {
                    val response = executor.executeWithRetry {
                        handleRequest(request.request, request.context)
                    }

                    if (Instant.now() >= expireAt) {
                        logger.warn("Event ${request.request::class.java.name} for tenant ${request.context.tenantId} " +
                                "is no longer valid, expired at $expireAt")
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
                    "Failed to handle ${request.request::class.java.name} for tenant ${request.context.tenantId}",
                    throwable
                )
                externalEventResponseFactory.platformError(request.flowExternalEventContext, throwable)
            }
        }
    }

    private fun handleRequest(request: Any, context: CryptoRequestContext): Any {
        // What about if cryptoOpsClient has gone to DOWN or ERROR out at this point? 
        // Then CryptoOpsClientComponent will throw AbstractComponentNotReadyFunction, and the retry logic
        // will keep repeating it until timeout or the cryptoOpsClient potentially comes back. 

        // For example, if we get an UnknownHostException from Kafka client code then the crypto ops client
        // will be marked as DOWN or ERROR (depending on what catches it; certainly if it made it to the top of the
        // TreadLooper it will cause the status to go to ERROR)

        return CordaMetrics.Metric.CryptoProcessorExecutionTime.builder()
            .withTag(CordaMetrics.Tag.OperationName, request::class.java.simpleName)
            .build()
            .recordCallable<Any> {
                when (request) {
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

                    is ByIdsFlowQuery ->
                        cryptoOpsClient.lookupKeysByFullIdsProxy(context.tenantId, request.fullKeyIds)

                    else ->
                        throw IllegalArgumentException("Unknown request type ${request::class.java.name}")
                }
            }!!
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
