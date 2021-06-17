package net.corda.p2p.gateway.messaging

import net.corda.v5.base.util.NetworkHostAndPort
import java.net.InetSocketAddress

fun InetSocketAddress.toHostAndPort() = NetworkHostAndPort(this.hostName, this.port)