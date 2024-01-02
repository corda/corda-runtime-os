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
import net.corda.utilities.trace
import net.corda.v5.crypto.DigestAlgorithmName
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.EnumMap
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

    override fun send(message: MediatorMessage<*>): MediatorMessage<*>? {
        return try {
            log.trace { "Received RPC external event send request for endpoint ${message.endpoint()}" }
            processMessage(message)
        } catch (e: Exception) {
            handleExceptions(e, message.endpoint())
        }
    }

    private fun processMessage(message: MediatorMessage<*>): MediatorMessage<*>? {
        val request = buildHttpRequest(message)
        val response = sendWithRetry(request)

        val deserializedResponse = deserializePayload(response.body())

        return deserializedResponse?.let {
            MediatorMessage(deserializedResponse, mutableMapOf("statusCode" to response.statusCode()))
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

        return builder.build()
    }

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
