package net.corda.messaging.mediator

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.messaging.api.exception.CordaHTTPClientErrorException
import net.corda.messaging.api.exception.CordaHTTPServerErrorException
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_ENDPOINT
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_KEY
import net.corda.messaging.utils.HTTPRetryConfig
import net.corda.messaging.utils.HTTPRetryExecutor
import net.corda.metrics.CordaMetrics
import net.corda.tracing.addTraceContextToHttpRequest
import net.corda.tracing.addTraceContextToMediatorMessage
import net.corda.tracing.traceSend
import net.corda.v5.crypto.DigestAlgorithmName
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.EnumMap
import java.util.LinkedList
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException

const val CORDA_REQUEST_KEY_HEADER = "corda-request-key"

@Suppress("LongParameterList")
class RPCClient(
    override val id: String,
    cordaAvroSerializerFactory: CordaAvroSerializationFactory,
    private val platformDigestService: PlatformDigestService,
    private val onSerializationError: ((ByteArray) -> Unit)?,
    private val httpClient: HttpClient
) : MessagingClient {
    private val deserializer = cordaAvroSerializerFactory.createAvroDeserializer({}, Any::class.java)

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val pendingResponses = LinkedList<CompletableFuture<MediatorMessage<*>>>()

    override fun send(message: MediatorMessage<*>): CompletableFuture<MediatorMessage<*>>? {
        val future = CompletableFuture<MediatorMessage<*>>()
        pendingResponses.addLast(future)
        pollPendingResponses(future)
        return future
    }

    private fun pollPendingResponses(future: CompletableFuture<MediatorMessage<*>>?, message: MediatorMessage<*>) {
        if (pendingResponses.isNotEmpty()) {
            try {
                future.complete(processMessage(message))
            } catch (e: Exception) {
                future.completeExceptionally(handleExceptions(e, message.endpoint()))
            }
        }
    }

    private fun processMessage(message: MediatorMessage<*>): MediatorMessage<*>? {
        val response = traceHttpSend(message.properties, URI(message.endpoint())) {
            val request = buildHttpRequest(message)
            sendWithRetry(request)
        }

        val deserializedResponse = deserializePayload(response.body())

        // Convert the response to an instance of the MediatorMessage class and enrich the instance with a trace context
        return deserializedResponse?.let {
            addTraceContextToMediatorMessage(
                MediatorMessage(deserializedResponse, mutableMapOf("statusCode" to response.statusCode())),
                message.properties
            )
        }
    }

    private fun deserializePayload(payload: ByteArray): Any? {
        return try {
            when {
                payload.isEmpty() -> null
                else -> deserializer.deserialize(payload)
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to deserialize payload of size ${payload.size} bytes due to: ${e.message}"
            log.warn(errorMsg, e)
            onSerializationError?.invoke(errorMsg.toByteArray())
            throw e
        }
    }

    private inline fun <T> traceHttpSend(traceHeaders: Map<String, Any>, uri: URI, send: () -> T): T {
        val traceContext = traceSend(traceHeaders, "http client - send request to path ${uri.path}")

        traceContext.traceTag("path", uri.path.toString())

        return traceContext.markInScope().use {
            try {
                val response = send()
                traceContext.finish()
                response
            } catch (ex: Exception) {
                traceContext.errorAndFinish(ex)
                throw ex
            }
        }
    }

    private fun buildHttpRequest(message: MediatorMessage<*>): HttpRequest {

        val builder = HttpRequest.newBuilder()
            .uri(URI(message.endpoint()))
            .POST(HttpRequest.BodyPublishers.ofByteArray(message.payload as ByteArray))

        // Add key HTTP header
        message.getPropertyOrNull(MSG_PROP_KEY)?.let { value ->
            val keyValue = if (value is ByteArray) {
                platformDigestService.hash(value, DigestAlgorithmName.SHA2_256).toHexString()
            } else {
                value.toString()
            }

            builder.header(CORDA_REQUEST_KEY_HEADER, keyValue)
        }

        builder.addTraceContext()

        return builder.build()
    }

    private fun HttpRequest.Builder.addTraceContext() =
        addTraceContextToHttpRequest(this)

    private fun sendWithRetry(request: HttpRequest): HttpResponse<ByteArray> {
        return HTTPRetryExecutor.withConfig(buildRetryConfig(request)) {
            httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
        }
    }

    private fun buildRetryConfig(request: HttpRequest): HTTPRetryConfig {
        val uri = request.method() + request.uri().toString()

        return HTTPRetryConfig.Builder()
            .retryOn(
                IOException::class.java,
                TimeoutException::class.java,
                CordaHTTPClientErrorException::class.java,
                CordaHTTPServerErrorException::class.java
            )
            .additionalMetrics(EnumMap(mutableMapOf(CordaMetrics.Tag.HttpRequestUri to uri)))
            .build()
    }

    private fun handleExceptions(e: Exception, endpoint: String): Exception {
        val exceptionToThrow = when (e) {
            is IOException,
            is InterruptedException,
            is TimeoutException,
            is CordaHTTPClientErrorException,
            is CordaHTTPServerErrorException -> {
                log.warn("Intermittent error in RPCClient request $endpoint: ", e)
                CordaMessageAPIIntermittentException(e.message, e)
            }

            is IllegalArgumentException,
            is SecurityException -> {
                log.warn("Fatal error in RPCClient request $endpoint: ", e)
                CordaMessageAPIFatalException(e.message, e)
            }

            else -> {
                log.warn("Unhandled exception in RPCClient request $endpoint: ", e)
                e
            }
        }

        return exceptionToThrow
    }

    override fun close() {
        // Nothing to do here
    }

    private fun MediatorMessage<*>.endpoint(): String {
        return getProperty<String>(MSG_PROP_ENDPOINT)
    }
}
