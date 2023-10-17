package net.corda.messaging.mediator

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.messaging.api.exception.CordaHTTPClientErrorException
import net.corda.messaging.api.exception.CordaHTTPServerErrorException
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_ENDPOINT
import net.corda.messaging.utils.HTTPRetryConfig
import net.corda.messaging.utils.HTTPRetryExecutor
import net.corda.metrics.CordaMetrics
import net.corda.utilities.debug
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class RPCClient(
    override val id: String,
    cordaAvroSerializerFactory: CordaAvroSerializationFactory,
    private val onSerializationError: ((ByteArray) -> Unit)?,
    private val httpClient: HttpClient,
    private val retryConfig: HTTPRetryConfig =
        HTTPRetryConfig.Builder()
            .retryOn(IOException::class.java, TimeoutException::class.java)
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
            log.trace("Received RPC external event send request for endpoint ${message.endpoint()}")
            processMessage(message)
        } catch (e: Exception) {
            handleExceptions(e)
            null
        }
    }

    private fun processMessage(message: MediatorMessage<*>): MediatorMessage<*> {
        val request = buildHttpRequest(message)
        val response = sendWithRetry(request)

        checkResponseStatus(response.statusCode())

        val deserializedResponse = deserializePayload(response.body())
        return MediatorMessage(deserializedResponse, mutableMapOf("statusCode" to response.statusCode()))
    }


    private fun deserializePayload(payload: ByteArray): Any {
        return try {
            deserializer.deserialize(payload)!!
        } catch (e: Exception) {
            val errorMsg = "Failed to deserialize payload of size ${payload.size} bytes due to: ${e.message}"
            log.warn(errorMsg)
            onSerializationError?.invoke(errorMsg.toByteArray())
            throw e
        }
    }

    private fun buildHttpRequest(message: MediatorMessage<*>): HttpRequest {
        return HttpRequest.newBuilder()
            .uri(URI(message.endpoint()))
            .POST(HttpRequest.BodyPublishers.ofByteArray(message.payload as ByteArray))
            .build()
    }

    private fun sendWithRetry(request: HttpRequest): HttpResponse<ByteArray> {
        val startTime = Instant.now().toEpochMilli()
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
        val endTime = Instant.now().toEpochMilli()
        CordaMetrics.Metric.Messaging.HTTPRPCResponseTime.builder()
            .withTag(CordaMetrics.Tag.OperationStatus, operationStatus)
            .withTag(CordaMetrics.Tag.HttpMethod, request.method())
            .withTag(CordaMetrics.Tag.HttpRequestUri, request.uri().toString())
            .withTag(CordaMetrics.Tag.HttpResponseCode, response?.statusCode().toString())
            .build()
            .record(endTime - startTime, TimeUnit.MILLISECONDS)

        if (operationStatus == SUCCESS) {
            CordaMetrics.Metric.Messaging.HTTPRPCResponseSize.builder()
                .withTag(CordaMetrics.Tag.OperationStatus, operationStatus)
                .withTag(CordaMetrics.Tag.HttpMethod, request.method())
                .withTag(CordaMetrics.Tag.HttpRequestUri, request.uri().toString())
                .withTag(CordaMetrics.Tag.HttpResponseCode, response?.statusCode().toString())
                .build()
                .record(response?.body()?.size?.toDouble() ?: 0.0)
        }
    }

    private fun checkResponseStatus(statusCode: Int) {
        log.trace("Received response with status code $statusCode")
        when (statusCode) {
            in 400..499 -> throw CordaHTTPClientErrorException(statusCode, "Server returned status code $statusCode.")
            in 500..599 -> throw CordaHTTPServerErrorException(statusCode, "Server returned status code $statusCode.")
        }
    }

    private fun handleExceptions(e: Exception) {
        when (e) {
            is IOException -> log.warn("Network or IO operation error in RPCClient: ", e)
            is InterruptedException -> log.warn("Operation was interrupted in RPCClient: ", e)
            is IllegalArgumentException -> log.warn("Invalid argument provided in RPCClient call: ", e)
            is SecurityException -> log.warn("Security violation detected in RPCClient: ", e)
            is IllegalStateException -> log.warn("Coroutine state error in RPCClient: ", e)
            is CordaHTTPClientErrorException -> log.warn("Client-side HTTP error in RPCClient: ", e)
            is CordaHTTPServerErrorException -> log.warn("Server-side HTTP error in RPCClient: ", e)
            else -> log.warn("Unhandled exception in RPCClient: ", e)
        }

        throw e
    }

    override fun close() {
        // Nothing to do here
    }

    private fun MediatorMessage<*>.endpoint(): String {
        return getProperty<String>(MSG_PROP_ENDPOINT)
    }
}
