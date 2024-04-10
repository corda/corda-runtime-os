package net.corda.p2p.gateway.messaging.http

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import java.lang.IllegalArgumentException
import java.net.URI
import java.net.URL

/**
 * Class contains helper methods used to handle requests and responses
 */
class HttpHelper {

    companion object {
        /**
         * Protocol scheme expected by the server
         */
        const val SCHEME = "https"

        /**
         * Creates a simple POST request with keepalive and JSON content type
         * @param message payload bytes to be added to the body of the request
         * @param uri URI of the HTTP server for which this request is intended
         */
        fun createRequest(message: ByteArray, uri: URI): HttpRequest {
            val content = Unpooled.copiedBuffer(message)
            return DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                URL(SCHEME, uri.host, uri.port, uri.path).toString(),
                content,
            ).apply {
                headers()
                    .set(HttpHeaderNames.HOST, uri.host)
                    .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
                    .set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.APPLICATION_JSON)
                    .set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                    .set(HttpHeaderNames.CONTENT_LENGTH, content().readableBytes())
            }
        }

        /**
         * Creates an HTTP response with keepalive and JSON content type
         * @param message payload bytes to be added to the body of the response
         * @param status response status code describing outcome as per the RFC
         */
        fun createResponse(message: ByteArray?, status: HttpResponseStatus): HttpResponse {
            val content = if (message != null) Unpooled.copiedBuffer(message) else Unpooled.EMPTY_BUFFER
            return DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content).apply {
                headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                    .setInt(HttpHeaderNames.CONTENT_LENGTH, content().readableBytes())
                    .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
            }
        }

        /**
         * Extension function which validates an incoming request.
         * @return an [HttpResponseStatus] containing the status code
         */
        fun HttpRequest.validate(maxRequestSize: Long, urlPaths: Collection<String>): HttpResponseStatus {
            try {
                val uri = URI.create(this.uri()).normalize()

                if (!urlPaths.contains(uri.path)) {
                    return HttpResponseStatus.NOT_FOUND
                }
            } catch (e: IllegalArgumentException) {
                // The URI string in the request is invalid - cannot be used to instantiate URI object
                return HttpResponseStatus.BAD_REQUEST
            }

            if (this.protocolVersion() != HttpVersion.HTTP_1_1) {
                return HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED
            }

            if (this.method() != HttpMethod.POST) {
                return HttpResponseStatus.NOT_IMPLEMENTED
            }

            val contentLength = this.headers()[HttpHeaderNames.CONTENT_LENGTH]?.toLongOrNull() ?: return HttpResponseStatus.LENGTH_REQUIRED
            if (contentLength > maxRequestSize) {
                return HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE
            }

            if (!HttpHeaderValues.APPLICATION_JSON.contentEqualsIgnoreCase(this.headers()[HttpHeaderNames.CONTENT_TYPE])) {
                return HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE
            }

            return HttpResponseStatus.OK
        }
    }
}
