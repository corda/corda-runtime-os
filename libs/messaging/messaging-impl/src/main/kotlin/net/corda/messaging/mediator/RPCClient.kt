package net.corda.messaging.mediator

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.records.Record
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("ForbiddenComment")
class RPCClient(
    override val id: String,
    cordaAvroSerializerFactory: CordaAvroSerializationFactory,
    private val onSerializationError: ((ByteArray) -> Unit)?,
    httpClientFactory: () -> HttpClient = { HttpClient.newBuilder().build() }
) : MessagingClient {
    private val httpClient: HttpClient = httpClientFactory()

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val serializer = cordaAvroSerializerFactory.createAvroSerializer<Any> {}
    private val deserializer = cordaAvroSerializerFactory.createAvroDeserializer({}, Record::class.java)

    // TODO Temporary; need to find correct exceptions for these cases
    class HttpClientErrorException(val statusCode: Int, message: String): Exception(message)
    class HttpServerErrorException(val statusCode: Int, message: String): Exception(message)

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun send(message: MediatorMessage<*>): Deferred<MediatorMessage<*>?> {
        val deferred = CompletableDeferred<MediatorMessage<*>?>()

        scope.launch {
            try {
                val result = processMessage(message)
                deferred.complete(result)
            } catch (e: Exception) {
                handleExceptions(e, deferred)
            }
        }

        return deferred;
    }

    private fun processMessage(message: MediatorMessage<*>): MediatorMessage<*> {
        val payload = serializePayload(message)

        val request = HttpRequest.newBuilder()
            .uri(URI("http://corda-flow-mapper-worker-internal-service:7000"))
            .PUT(HttpRequest.BodyPublishers.ofByteArray(payload))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())

        // TODO: The HTTP client should be abstracted out into its own class which handles retries
        if (response.statusCode() in 400..499) {
            throw HttpClientErrorException(response.statusCode(), "Client error response from server.")
        } else if (response.statusCode() in 500..599) {
            throw HttpServerErrorException(response.statusCode(), "Server error response from server.")
        }

        val deserializedResponse = deserializePayload(response.body())

        return MediatorMessage(deserializedResponse, mutableMapOf("statusCode" to response.statusCode()))
    }

    private fun serializePayload(message: MediatorMessage<*>): ByteArray {
        return try {
            serializer.serialize(message.payload as Record<*, *>)!!
        } catch (e: Exception) {
            val errorMsg = "Failed to serialize instance of class type ${
                message.payload?.let { it::class.java.name } ?: "null"
            }."
            onSerializationError?.invoke(errorMsg.toByteArray())
            throw(e)
        }
    }

    private fun deserializePayload(payload: ByteArray): Record<*,*> {
        return try {
            deserializer.deserialize(payload)!!
        } catch (e: Exception) {
            val errorMsg = "Failed to deserialize payload of size ${payload.size} bytes due to: ${e.message}"
            onSerializationError?.invoke(errorMsg.toByteArray())
            throw(e)
        }
    }

    @Suppress("ForbiddenComment")
    // TODO This is placeholder exception handling
    private fun handleExceptions(e: Exception, deferred: CompletableDeferred<MediatorMessage<*>?>) {
        when (e) {
            is IOException,
            is InterruptedException,
            is IllegalArgumentException,
            is SecurityException -> log.error("HTTP error in RPCClient: ", e)

            is IllegalStateException -> log.error("Coroutine error in RPCClient: ", e)

            else -> log.error("Unhandled exception in RPCClient: ", e)
        }

        deferred.completeExceptionally(e)
    }

    override fun close() {
        job.cancel()
    }
}
