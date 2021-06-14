package net.corda.p2p.gateway.messaging.http

import net.corda.v5.base.util.NetworkHostAndPort

data class ConnectionChangeEvent(val remoteAddress: NetworkHostAndPort, val connected: Boolean) {
    override fun toString() = "Connection change target: $remoteAddress connected: $connected"
}