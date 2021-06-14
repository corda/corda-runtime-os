package net.corda.p2p.gateway.messaging

import net.corda.v5.base.util.NetworkHostAndPort
import java.net.InetSocketAddress

/**
 * Endpoint suffix for POST requests.
 */
const val ENDPOINT = "/gateway/send"

fun InetSocketAddress.toHostAndPort() = NetworkHostAndPort(this.hostName, this.port)