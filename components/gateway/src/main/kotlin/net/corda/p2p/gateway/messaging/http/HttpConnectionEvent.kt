package net.corda.p2p.gateway.messaging.http

import io.netty.channel.Channel

data class HttpConnectionEvent(val channel: Channel)
