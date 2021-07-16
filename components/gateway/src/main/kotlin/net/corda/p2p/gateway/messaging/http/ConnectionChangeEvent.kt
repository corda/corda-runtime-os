package net.corda.p2p.gateway.messaging.http

import java.net.SocketAddress

data class ConnectionChangeEvent(val remoteAddress: SocketAddress, val connected: Boolean) {
    override fun toString() = "Connection change target: $remoteAddress connected: $connected"
}