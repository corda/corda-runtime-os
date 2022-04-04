package net.corda.crypto.service.impl.flow

import net.corda.crypto.client.CryptoOpsProxyClient
import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.crypto.service.impl.WireProcessor
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.crypto.wire.ops.flow.commands.GenerateFreshKeyFlowCommand
import net.corda.data.crypto.wire.ops.flow.commands.SignFlowCommand
import net.corda.data.crypto.wire.ops.flow.queries.FilterMyKeysFlowQuery
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import java.time.Instant
import java.util.UUID

class CryptoFlowOpsProcessor(
    private val cryptoOpsClient: CryptoOpsProxyClient
) : WireProcessor(handlers), DurableProcessor<String, FlowOpsRequest> {
    companion object {
        private val logger = contextLogger()
        private val handlers = mapOf<Class<*>, Class<out Handler<out Any>>>(
            FilterMyKeysFlowQuery::class.java to FilterMyKeysFlowQueryHandler::class.java,
            GenerateFreshKeyFlowCommand::class.java to GenerateFreshKeyFlowCommandHandler::class.java,
            SignFlowCommand::class.java to SignFlowCommandHandler::class.java
        )
    }

    override val keyClass: Class<String> = String::class.java

    override val valueClass: Class<FlowOpsRequest> = FlowOpsRequest::class.java

    override fun onNext(events: List<Record<String, FlowOpsRequest>>): List<Record<*, *>> =
        events.mapNotNull { onNext(it) }

    private fun onNext(event: Record<String, FlowOpsRequest>): Record<*, *>? {
        val request = event.value
        if (request == null) {
            logger.error("Unexpected null payload for event with the key={} in topic={}", event.key, event.topic)
            return null // cannot send any error back as have no idea where to send to
        }
        val responseTopic = getResponseTopic(request)
        if (responseTopic.isNullOrBlank()) {
            logger.error(
                "Unexpected null value for response topic in event with the key={} in topic={}",
                event.key,
                event.topic
            )
            return null // cannot send any error back as have no idea where to send to
        }
        val expireAt = getRequestExpireAt(request)
        if (Instant.now() >= expireAt) {
            logger.error(
                "Event {} for tenant {} is no longer valid, expired at {}",
                request.request::class.java,
                request.context.tenantId,
                expireAt
            )
            return Record(
                responseTopic,
                event.key,
                createErrorResponse(request, "Expired at $expireAt")
            )
        }
        return try {
            logger.info("Handling {} for tenant {}", request.request::class.java.name, request.context.tenantId)
            val response = getHandler(request.request::class.java, cryptoOpsClient)
                .handle(request.context, request.request)
            if (Instant.now() >= expireAt) {
                logger.error(
                    "Event {} for tenant {} is no longer valid, expired at {}",
                    request.request::class.java,
                    request.context.tenantId,
                    expireAt
                )
                return Record(
                    responseTopic,
                    event.key,
                    createErrorResponse(request, "Expired at $expireAt")
                )
            }
            val result = Record(
                responseTopic,
                event.key,
                FlowOpsResponse(createResponseContext(request), response)
            )
            logger.debug(
                "Handled {} for tenant {} with {}",
                request.request::class.java.name,
                request.context.tenantId
            )
            result
        } catch (e: Throwable) {
            val message = "Failed to handle ${request.request::class.java} for tenant ${request.context.tenantId}"
            logger.error(message, e)
            return Record(
                responseTopic,
                event.key,
                createErrorResponse(request, e.message ?: e::class.java.name)
            )
        }
    }

    private fun getRequestExpireAt(request: FlowOpsRequest): Instant =
        request.context.requestTimestamp.plusSeconds(getRequestValidityWindowSeconds(request))

    private fun getRequestValidityWindowSeconds(request: FlowOpsRequest): Long {
        return request.context.other.items.singleOrNull {
            it.key == CryptoFlowOpsTransformer.REQUEST_TTL_KEY
        }?.value?.toLong() ?: 300
    }

    private fun getResponseTopic(request: FlowOpsRequest): String? {
        return request.context.other.items.singleOrNull {
            it.key == CryptoFlowOpsTransformer.RESPONSE_TOPIC
        }?.value
    }

    private fun createErrorResponse(request: FlowOpsRequest, error: String): FlowOpsResponse =
        FlowOpsResponse(createResponseContext(request, error), CryptoNoContentValue())

    private fun createResponseContext(request: FlowOpsRequest, error: String? = null) = CryptoResponseContext(
        request.context.requestingComponent,
        request.context.requestTimestamp,
        request.context.requestId,
        Instant.now(),
        request.context.tenantId,
        KeyValuePairList(request.context.other.items.toList())
    ).also {
        if (!error.isNullOrBlank()) {
            it.other.items.add(KeyValuePair(CryptoFlowOpsTransformer.RESPONSE_ERROR_KEY, error))
        }
    }

    private class FilterMyKeysFlowQueryHandler(
        private val client: CryptoOpsProxyClient
    ) : Handler<FilterMyKeysFlowQuery> {
        override fun handle(context: CryptoRequestContext, request: FilterMyKeysFlowQuery): Any =
            client.filterMyKeysProxy(
                tenantId = context.tenantId,
                candidateKeys = request.keys
            )
    }

    private class GenerateFreshKeyFlowCommandHandler(
        private val client: CryptoOpsProxyClient
    ) : Handler<GenerateFreshKeyFlowCommand> {
        override fun handle(context: CryptoRequestContext, request: GenerateFreshKeyFlowCommand): Any =
            if (request.externalId.isNullOrBlank()) {
                client.freshKeyProxy(
                    tenantId = context.tenantId,
                    context = request.context
                )
            } else {
                client.freshKeyProxy(
                    tenantId = context.tenantId,
                    externalId = UUID.fromString(request.externalId),
                    context = request.context
                )
            }
    }

    private class SignFlowCommandHandler(
        private val client: CryptoOpsProxyClient
    ) : Handler<SignFlowCommand> {
        override fun handle(context: CryptoRequestContext, request: SignFlowCommand): Any =
            client.signProxy(
                tenantId = context.tenantId,
                publicKey = request.publicKey ,
                data = request.bytes,
                context = request.context
            )
    }
}