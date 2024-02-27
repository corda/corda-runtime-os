package net.corda.messaging.api.publisher

import net.corda.v5.base.exceptions.CordaRuntimeException
import java.net.URI
import java.net.http.HttpResponse

/**
 * Interface for posting requests of and receiving responses over HTTP.
 * HttpRpcClient instances can be created via the [PublisherFactory].
 */
interface HttpRpcClient {
    class HttpRpcException private constructor(
        val statusCode: Int?,
        val responseSize: Int?,
        message: String?,
        cause: Throwable?,
    ) :
        CordaRuntimeException(
            message,
            cause,
        ) {
        constructor(cause: Exception) :
                this(statusCode = null, responseSize = null, message = cause.message, cause = cause)
        constructor(response: HttpResponse<ByteArray>) :
                this(
                    statusCode = response.statusCode(),
                    responseSize = response.body().size,
                    message = "Server returned HTTP status code: ${response.statusCode()}",
                    cause = null,
                )
    }

    /**
     * Send a request [requestBody] to [uri] return the response of type R.
     * Return null if the HTTP response was successful but had empty body.
     * Throws HttpRpcException in case of en error.
     */
    fun <T : Any, R : Any> send(uri: URI, requestBody: T, clz: Class<R>): R?
}

/**
 * Send a request [requestBody] to [uri] return the response of type R.
 * Return null if the HTTP response was successful but had empty body.
 * Throws HttpRpcException in case of en error.
 */
inline fun <reified R : Any> HttpRpcClient.send(uri: URI, requestBody: Any): R? {
    return this.send(uri, requestBody, R::class.java)
}
