package net.corda.p2p.gateway.messaging.http

open class ListenerWithServer : HttpEventListener {
    var server: HttpServer? = null
}
