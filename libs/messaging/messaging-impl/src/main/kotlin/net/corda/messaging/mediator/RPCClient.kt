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
import net.corda.tracing.TraceUtils.extractTracingHeaders
import net.corda.tracing.traceSend
import net.corda.utilities.debug
import net.corda.utilities.trace
import net.corda.v5.crypto.DigestAlgorithmName
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeoutException

const val CORDA_REQUEST_KEY_HEADER = "corda-request-key"

@Suppress("LongParameterList")
class RPCClient(
    override val id: String,
    cordaAvroSerializerFactory: CordaAvroSerializationFactory,
    private val platformDigestService: PlatformDigestService,
    private val onSerializationError: ((ByteArray) -> Unit)?,
    private val httpClient: HttpClient,
    private val retryConfig: HTTPRetryConfig =
        HTTPRetryConfig.Builder()
            .retryOn(
                IOException::class.java,
                TimeoutException::class.java,
                CordaHTTPClientErrorException::class.java,
                CordaHTTPServerErrorException::class.java
            )
            .build()
) : MessagingClient {
    private val deserializer = cordaAvroSerializerFactory.createAvroDeserializer({}, Any::class.java)

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val SUCCESS: String = "SUCCESS"
        private const val FAILED: String = "FAILED"
    }

    override fun send(message: MediatorMessage<*>): MediatorMessage<*>? {
        return try {
            log.trace { "Received RPC external event send request for endpoint ${message.endpoint()}" }
            processMessage(message)
        } catch (e: Exception) {
            handleExceptions(e, message.endpoint())
        }
    }

    private fun processMessage(message: MediatorMessage<*>): MediatorMessage<*>? {
        // Extract the tracing headers from the mediator message, so they can be
        // copied into the HTTP request and keep the traceability intact
        val tracingHeaders = message.extractTracingHeaders()

        // Build the HTTP request based on the mediator message and the tracing headers
        val request = buildHttpRequest(message, tracingHeaders)

        val response = traceHttpSend(tracingHeaders, request.uri()) {
            sendWithRetry(request)
        }

        val deserializedResponse = deserializePayload(response.body())

        return deserializedResponse?.let {
            val headers: MutableMap<String, Any> = mutableMapOf("statusCode" to response.statusCode())
            tracingHeaders.forEach { (k, v) -> headers[k] = v }
            MediatorMessage(deserializedResponse, headers)
        }
    }

    private inline fun<T> traceHttpSend(tracingHeaders: List<Pair<String, String>>, uri: URI, send: ()-> T): T {
        val traceContext = traceSend(tracingHeaders, "http - send - path - ${uri.path}")

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

    private fun buildHttpRequest(message: MediatorMessage<*>, extraHeaders: List<Pair<String, String>>): HttpRequest {
        // Local auxiliary function that adds headers in a list to the HTTP request
        fun HttpRequest.Builder.headers(headers: List<Pair<String, String>>) {
            for ((name, value) in headers) {
                header(name, value)
            }
        }

        val builder = HttpRequest.newBuilder()
            .uri(URI(message.endpoint()))
            .POST(HttpRequest.BodyPublishers.ofByteArray(message.payload as ByteArray))

        // Add corda request key to the HTTP header in the request if the key is present in the message
        message.extractCordaRequestKeyHeader()?.let { (name, value) -> builder.headers(name, value) }

        // Add any other header to the HTTP request that are deemed relevant
        builder.headers(extraHeaders)

        // Build the request and return
        return builder.build()
    }

    private fun<T: Any> MediatorMessage<T>.extractCordaRequestKeyHeader(): Pair<String, String>? {
        val value = getPropertyOrNull(MSG_PROP_KEY)?.let { value ->
            if (value is ByteArray) {
                platformDigestService.hash(value, DigestAlgorithmName.SHA2_256).toHexString()
            } else {
                value.toString()
            }
        } ?: return null

        return CORDA_REQUEST_KEY_HEADER to value
    }

    private fun sendWithRetry(request: HttpRequest): HttpResponse<ByteArray> {
        val startTime = System.nanoTime()
        return try {
            val response = HTTPRetryExecutor.withConfig(retryConfig) {
                httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
            }
            buildMetricForResponse(startTime, SUCCESS, request, response)
            response
        } catch (ex: Exception) {
            log.debug { "Catching exception in HttpClient sendWithRetry in order to log metrics, $ex" }
            buildMetricForResponse(startTime, FAILED, request)
            throw ex
        }
    }

    private fun buildMetricForResponse(
        startTime: Long,
        operationStatus: String,
        request: HttpRequest,
        response: HttpResponse<ByteArray>? = null
    ) {
        val endTime = System.nanoTime()
        val uri = request.method() + request.uri().toString()
        CordaMetrics.Metric.Messaging.HTTPRPCResponseTime.builder()
            .withTag(CordaMetrics.Tag.OperationStatus, operationStatus)
            .withTag(CordaMetrics.Tag.HttpRequestUri, uri)
            .withTag(CordaMetrics.Tag.HttpResponseCode, response?.statusCode().toString())
            .build()
            .record(Duration.ofNanos(endTime - startTime))

        if (response != null) {
            CordaMetrics.Metric.Messaging.HTTPRPCResponseSize.builder()
                .withTag(CordaMetrics.Tag.HttpRequestUri, uri)
                .build()
                .record(response.body().size.toDouble())
        }
    }

    private fun handleExceptions(e: Exception, endpoint: String): Nothing {
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

        throw exceptionToThrow
    }

    override fun close() {
        // Nothing to do here
    }

    private fun MediatorMessage<*>.endpoint(): String {
        return getProperty<String>(MSG_PROP_ENDPOINT)
    }
}
