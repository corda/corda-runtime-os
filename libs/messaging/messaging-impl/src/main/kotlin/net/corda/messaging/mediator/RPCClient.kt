package net.corda.messaging.mediator

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.TimeoutException
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.messaging.api.exception.CordaHTTPClientErrorException
import net.corda.messaging.api.exception.CordaHTTPServerErrorException
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_ENDPOINT
import net.corda.messaging.utils.HTTPRetryConfig
import net.corda.messaging.utils.HTTPRetryExecutor
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class RPCClient(
    override val id: String,
    cordaAvroSerializerFactory: CordaAvroSerializationFactory,
    private val onSerializationError: ((ByteArray) -> Unit)?,
    private val httpClient: HttpClient,
    private val retryConfig: HTTPRetryConfig =
        HTTPRetryConfig.Builder()
            .retryOn(IOException::class, TimeoutException::class)
            .build()
) : MessagingClient {
    private val deserializer = cordaAvroSerializerFactory.createAvroDeserializer({}, Any::class.java)

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun send(message: MediatorMessage<*>): MediatorMessage<*>? {
        return try {
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
            log.error(errorMsg)
            onSerializationError?.invoke(errorMsg.toByteArray())
            throw(e)
        }
    }

    private fun buildHttpRequest(message: MediatorMessage<*>): HttpRequest {
        return HttpRequest.newBuilder()
            .uri(URI(message.endpoint()))
            .POST(HttpRequest.BodyPublishers.ofByteArray(message.payload as ByteArray))
            .build()
    }

    private fun sendWithRetry(request: HttpRequest): HttpResponse<ByteArray> {
        return HTTPRetryExecutor.withConfig(retryConfig) {
            httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
        }
    }

    private fun checkResponseStatus(statusCode: Int) {
        when (statusCode) {
            in 400..499 -> throw CordaHTTPClientErrorException(statusCode, "Server returned status code $statusCode.")
            in 500..599 -> throw CordaHTTPServerErrorException(statusCode, "Server returned status code $statusCode.")
        }
    }

    private fun handleExceptions(e: Exception) {
        when (e) {
            is IOException -> log.error("Network or IO operation error in RPCClient: ", e)
            is InterruptedException -> log.error("Operation was interrupted in RPCClient: ", e)
            is IllegalArgumentException -> log.error("Invalid argument provided in RPCClient call: ", e)
            is SecurityException -> log.error("Security violation detected in RPCClient: ", e)
            is IllegalStateException -> log.error("Coroutine state error in RPCClient: ", e)
            is CordaHTTPClientErrorException -> log.error("Client-side HTTP error in RPCClient: ", e)
            is CordaHTTPServerErrorException -> log.error("Server-side HTTP error in RPCClient: ", e)
            else -> log.error("Unhandled exception in RPCClient: ", e)
        }

        throw e;
    }

    override fun close() {
        // Nothing to do here
    }

    private fun MediatorMessage<*>.endpoint(): String {
        return getProperty<String>(MSG_PROP_ENDPOINT)
    }
}
