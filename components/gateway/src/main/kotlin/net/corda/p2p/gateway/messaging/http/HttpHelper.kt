package net.corda.p2p.gateway.messaging.http

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.DefaultFullHttpResponse
import net.corda.v5.base.util.NetworkHostAndPort
import java.lang.IllegalArgumentException
import java.net.URI
import java.net.URL

/**
 * Class contains helper methods used to handle requests and responses
 */
class HttpHelper {

    companion object {
        /**
         * Endpoint suffix for POST requests.
         */
        const val ENDPOINT = "/gateway/send"

        /**
         * Protocol scheme expected by the server
         */
        const val SCHEME = "https"

        /**
         * Creates a simple POST request with keepalive and JSON content type
         * @param message payload bytes to be added to the body of the request
         * @param destination host and port of the HTTP server for which this request is intended
         */
        fun createRequest(message: ByteArray, destination: NetworkHostAndPort): HttpRequest {
            return DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                URL("https", destination.host, destination.port, ENDPOINT).toString()
            ).apply {
                val bbuf = Unpooled.copiedBuffer(message)
                headers().set(HttpHeaderNames.HOST, destination.host)
                    .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
                    .set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.APPLICATION_JSON)
                    .set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                    .set(HttpHeaderNames.CONTENT_LENGTH, bbuf.readableBytes())
                content().clear().writeBytes(bbuf)
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
         * @return an [HttpResponse] containing the status code and potentially an error message in the response body should
         * there be a need
         */
        //TODO: after endpoint is validated, specific validations on request headers should be done for that endpoint; this is to future proof the code
        fun HttpRequest.validate(): HttpResponse {
            try {
                val uri = URI.create(this.uri()).normalize()
                if (uri.scheme != SCHEME) {
                    return DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST)
                }

                //TODO: check authority against server address?
                if (uri.path != ENDPOINT) {
                    return DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND)
                }

            } catch (e: IllegalArgumentException) {
                //The URI string in the request is invalid - cannot be used to instantiate URI object
                return DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST)
            }

            if (this.protocolVersion() != HttpVersion.HTTP_1_1) {
                return DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED)
            }

            if (this.method() != HttpMethod.POST) {
                return DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_IMPLEMENTED)
            }

            if (this.headers()[HttpHeaderNames.CONTENT_LENGTH] == null) {
                return DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.LENGTH_REQUIRED)
            }

            if (HttpHeaderValues.APPLICATION_JSON.contentEqualsIgnoreCase(this.headers()[HttpHeaderNames.CONTENT_TYPE]))
            {
                return DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE)
            }

            return DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        }
    }
}