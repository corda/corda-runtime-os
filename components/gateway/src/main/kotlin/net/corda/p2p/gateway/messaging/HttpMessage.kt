package net.corda.p2p.gateway.messaging

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.v5.base.util.NetworkHostAndPort

/**
 * Used to deliver HTTP messages from the transport layer to the application layer
 */
class HttpMessage(
    val statusCode: HttpResponseStatus,
    var payload: ByteArray,
    val source: NetworkHostAndPort,
    val destination: NetworkHostAndPort
) {
    fun release() {
        payload = ByteArray(0)
    }

    override fun toString(): String {
        return "Status: $statusCode\nsource: $source\ndestination: $destination\npayload: $payload"
    }
}