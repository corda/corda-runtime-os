package net.corda.p2p.gateway.messaging.http

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.v5.base.util.EncodingUtils.toBase64
import java.net.SocketAddress

class HttpResponse(
    val statusCode: HttpResponseStatus,
    val payload: ByteArray,
    val source: SocketAddress,
    val destination: SocketAddress
) {

    override fun toString(): String {
        return "Status: $statusCode\nsource: $source\ndestination: $destination\npayload: ${toBase64(payload)}"
    }
}