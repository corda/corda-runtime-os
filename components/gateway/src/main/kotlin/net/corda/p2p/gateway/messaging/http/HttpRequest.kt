package net.corda.p2p.gateway.messaging.http

import net.corda.v5.base.util.toBase64
import java.net.SocketAddress

class HttpRequest(
    val payload: ByteArray,
    val source: SocketAddress,
    val destination: SocketAddress
) {

    override fun toString(): String {
        return "Source: $source\ndestination: $destination\npayload: ${payload.toBase64()}"
    }
}