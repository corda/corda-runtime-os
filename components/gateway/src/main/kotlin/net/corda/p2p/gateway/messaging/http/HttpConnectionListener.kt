package net.corda.p2p.gateway.messaging.http

import java.util.EventListener

interface HttpConnectionListener: EventListener {
    fun onOpen(event: HttpConnectionEvent) {}

    fun onClose(event: HttpConnectionEvent) {}
}