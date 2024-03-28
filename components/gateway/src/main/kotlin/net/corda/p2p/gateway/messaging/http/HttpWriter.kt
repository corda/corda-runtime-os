package net.corda.p2p.gateway.messaging.http

import io.netty.handler.codec.http.HttpResponseStatus
import java.net.SocketAddress

internal interface HttpWriter {
    fun write(
        statusCode: HttpResponseStatus,
        destination: SocketAddress,
        message: ByteArray = byteArrayOf(),
    )
}
