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
import net.corda.metrics.CordaMetrics
import net.corda.tracing.addTraceContextToHttpRequest
import net.corda.tracing.addTraceContextToMediatorMessage
import net.corda.tracing.traceSend
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

    override fun send(message: MediatorMessage<*>): CompletableFuture<MediatorMessage<*>?> {
        return try {
            log.trace { "Received RPC external event send request for endpoint ${message.endpoint()}" }
            processMessage(message)
        } catch (e: Exception) {
            handleExceptions(e, message.endpoint())
        }
    }

    private fun processMessage(message: MediatorMessage<*>): CompletableFuture<MediatorMessage<*>?> {
        return traceHttpSend(message.properties, URI(message.endpoint())) {
            log.info("RPC payload ${message.payload?.javaClass?.simpleName}")
            val startTime = System.nanoTime()
            val request = buildHttpRequest(message)
            sendWithRetry(request).thenApply { response ->
                buildMetricForResponse(startTime, "SUCCESS", response)
                val deserializedResponse = deserializePayload(response.body())
                // Convert the response to an instance of the MediatorMessage class and enrich the instance with a trace context
                deserializedResponse?.let {
                    addTraceContextToMediatorMessage(
                        MediatorMessage(deserializedResponse, mutableMapOf("statusCode" to response.statusCode())),
                        message.properties
                    )
                }
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

    private fun sendWithRetry(request: HttpRequest): CompletableFuture<HttpResponse<ByteArray>> {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
    }

    private fun buildMetricForResponse(
        startTime: Long,
        operationStatus: String,
        response: HttpResponse<ByteArray>? = null
    ) {
        val endTime = System.nanoTime()
        recordResponseTimeMetric(startTime, endTime, operationStatus, response)
    }

    private fun recordResponseTimeMetric(
        startTime: Long,
        endTime: Long,
        operationStatus: String,
        response: HttpResponse<ByteArray>?
    ) {
        CordaMetrics.Metric.Messaging.HTTPRPCResponseTime.builder()
            .withTag(CordaMetrics.Tag.OperationStatus, operationStatus)
            .withTag(CordaMetrics.Tag.HttpResponseCode, response?.statusCode().toString())
            .build()
            .record(Duration.ofNanos(endTime - startTime))
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
