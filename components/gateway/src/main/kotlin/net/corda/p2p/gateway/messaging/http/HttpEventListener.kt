package net.corda.p2p.gateway.messaging.http

import java.util.*

interface HttpEventListener: EventListener {
    fun onOpen(event: HttpConnectionEvent) {}

    fun onClose(event: HttpConnectionEvent) {}

    fun onMessage(message: HttpMessage) {}
}