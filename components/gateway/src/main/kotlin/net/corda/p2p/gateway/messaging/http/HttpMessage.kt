package net.corda.p2p.gateway.messaging.http

import io.netty.handler.codec.http.HttpResponseStatus
import java.net.SocketAddress

/**
 * Used to deliver HTTP messages from the transport layer to the application layer
 */
class HttpMessage(
    val statusCode: HttpResponseStatus,
    val payload: ByteArray,
    val source: SocketAddress,
    val destination: SocketAddress
) {

    override fun toString(): String {
        return "Status: $statusCode\nsource: $source\ndestination: $destination\npayload: $payload"
    }
}