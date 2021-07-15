package net.corda.p2p.gateway.messaging.http

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.v5.base.util.NetworkHostAndPort

/**
 * Used to deliver HTTP messages from the transport layer to the application layer
 */
class HttpMessage(
    val statusCode: HttpResponseStatus,
    val payload: ByteArray,
    val source: NetworkHostAndPort,
    val destination: NetworkHostAndPort
) {

    override fun toString(): String {
        return "Status: $statusCode\nsource: $source\ndestination: $destination\npayload: $payload"
    }
}