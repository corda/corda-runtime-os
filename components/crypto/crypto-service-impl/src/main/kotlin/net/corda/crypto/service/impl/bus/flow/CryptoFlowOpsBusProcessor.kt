package net.corda.crypto.service.impl.bus.flow

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.crypto.client.CryptoOpsProxyClient
import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.crypto.impl.config.flowBusProcessor
import net.corda.crypto.impl.config.toCryptoConfig
import net.corda.crypto.impl.retrying.CryptoRetryingExecutor
import net.corda.crypto.service.impl.WireProcessor
import net.corda.data.ExceptionEnvelope
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.crypto.wire.ops.flow.commands.SignFlowCommand
import net.corda.data.crypto.wire.ops.flow.queries.FilterMyKeysFlowQuery
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.data.flow.event.external.ExternalEventResponseErrorType.PLATFORM_ERROR
import net.corda.data.flow.event.external.ExternalEventResponseErrorType.RETRY
import net.corda.data.flow.event.external.ExternalEventResponseExceptionEnvelope
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import net.corda.v5.base.exceptions.BackoffStrategy
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.time.Instant

class CryptoFlowOpsBusProcessor(
    private val cryptoOpsClient: CryptoOpsProxyClient,
    event: ConfigChangedEvent
) : WireProcessor(handlers), DurableProcessor<String, FlowOpsRequest> {
    companion object {
        private val logger = contextLogger()
        private val handlers = mapOf<Class<*>, Class<out Handler<out Any>>>(
            FilterMyKeysFlowQuery::class.java to FilterMyKeysFlowQueryHandler::class.java,
            SignFlowCommand::class.java to SignFlowCommandHandler::class.java
        )
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

    // need to convert the error responses into the external event error handling
    private fun onNext(event: Record<String, FlowOpsRequest>): Record<*, *>? {
        val request = event.value
        if (request == null) {
            logger.error("Unexpected null payload for event with the key={} in topic={}", event.key, event.topic)
            return null // cannot send any error back as have no idea where to send to
        }
        
        val flowId = request.flowExternalEventContext.flowId
        val requestId = request.flowExternalEventContext.requestId
        val expireAt = getRequestExpireAt(request)
        
        if (Instant.now() >= expireAt) {
            logger.error(
                "Event {} for tenant {} is no longer valid, expired at {}",
                request.request::class.java,
                request.context.tenantId,
                expireAt
            )
            return createErrorResponse(request, RETRY, "Expired", "Expired at $expireAt")
        }
        return try {
            logger.info("Handling {} for tenant {}", request.request::class.java.name, request.context.tenantId)
            val handler = getHandler(request.request::class.java, cryptoOpsClient)
            val response = executor.executeWithRetry {
                handler.handle(request.context, request.request)
            }
            if (Instant.now() >= expireAt) {
                logger.error(
                    "Event {} for tenant {} is no longer valid, expired at {}",
                    request.request::class.java,
                    request.context.tenantId,
                    expireAt
                )
                return createErrorResponse(request, RETRY, "Expired", "Expired at $expireAt")
            }
            val result = Record(
                FLOW_EVENT_TOPIC,
                flowId,
                FlowEvent(
                    flowId,
                    ExternalEventResponse.newBuilder()
                        .setRequestId(requestId)
                        .setChunkNumber(null)
                        .setNumberOfChunks(null)
                        .setPayload(FlowOpsResponse(createResponseContext(request), response, null))
                        .setData(null)
                        .setExceptionEnvelope(null)
                        .setTimestamp(Instant.now())
                        .build()
                )
            )
            logger.debug { "Handled ${request.request::class.java.name} for tenant ${request.context.tenantId}" }
            result
        } catch (e: Throwable) {
            val message = "Failed to handle ${request.request::class.java} for tenant ${request.context.tenantId}"
            logger.error(message, e)
            return createErrorResponse(request, PLATFORM_ERROR, e::class.java.name, e.message ?: e::class.java.name)
        }
    }

    private fun getRequestExpireAt(request: FlowOpsRequest): Instant =
        request.context.requestTimestamp.plusSeconds(getRequestValidityWindowSeconds(request))

    private fun getRequestValidityWindowSeconds(request: FlowOpsRequest): Long {
        return request.context.other.items.singleOrNull {
            it.key == CryptoFlowOpsTransformer.REQUEST_TTL_KEY
        }?.value?.toLong() ?: 300
    }

    private fun createErrorResponse(
        request: FlowOpsRequest,
        externalEventResponseErrorType: ExternalEventResponseErrorType,
        errorType: String,
        errorMessage: String
    ): Record<String, FlowEvent> {
        return Record(
            FLOW_EVENT_TOPIC,
            request.flowExternalEventContext.flowId,
            FlowEvent(
                request.flowExternalEventContext.flowId,
                ExternalEventResponse.newBuilder()
                    .setRequestId(request.flowExternalEventContext.requestId)
                    .setChunkNumber(null)
                    .setNumberOfChunks(null)
                    .setPayload(null)
                    .setData(null)
                    .setExceptionEnvelope(
                        ExternalEventResponseExceptionEnvelope(
                            externalEventResponseErrorType,
                            ExceptionEnvelope(errorType, errorMessage)
                        )
                    )
                    .setTimestamp(Instant.now())
                    .build()
            )
        )
    }

    private fun createResponseContext(request: FlowOpsRequest) = CryptoResponseContext(
        request.context.requestingComponent,
        request.context.requestTimestamp,
        request.context.requestId,
        Instant.now(),
        request.context.tenantId,
        KeyValuePairList(request.context.other.items.toList())
    )

    private class FilterMyKeysFlowQueryHandler(
        private val client: CryptoOpsProxyClient
    ) : Handler<FilterMyKeysFlowQuery> {
        override fun handle(context: CryptoRequestContext, request: FilterMyKeysFlowQuery): Any =
            client.filterMyKeysProxy(
                tenantId = context.tenantId,
                candidateKeys = request.keys
            )
    }

    private class SignFlowCommandHandler(
        private val client: CryptoOpsProxyClient
    ) : Handler<SignFlowCommand> {
        override fun handle(context: CryptoRequestContext, request: SignFlowCommand): Any =
            client.signProxy(
                tenantId = context.tenantId,
                publicKey = request.publicKey,
                signatureSpec = request.signatureSpec,
                data = request.bytes,
                context = request.context
            )
    }
}