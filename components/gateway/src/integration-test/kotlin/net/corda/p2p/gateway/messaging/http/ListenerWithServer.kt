package net.corda.p2p.gateway.messaging.http

abstract class ListenerWithServer : HttpServerListener {
    var server: HttpServer? = null
}
