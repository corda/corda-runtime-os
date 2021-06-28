package net.corda.p2p.gateway.messaging

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.v5.base.util.NetworkHostAndPort

/**
 * [ApplicationMessage] implementation. Used to deliver response messags from the transport layer to the application layer
 */
class ResponseMessage(
    val statusCode: HttpResponseStatus,
    override var payload: ByteArray,
    override val source: NetworkHostAndPort,
    override val destination: NetworkHostAndPort
) : ApplicationMessage {
    override fun release() {
        payload = ByteArray(0)
    }

    override fun toString(): String {
        return "Status: $statusCode\nsource: $source\ndestination: $destination\npayload: $payload"
    }
}