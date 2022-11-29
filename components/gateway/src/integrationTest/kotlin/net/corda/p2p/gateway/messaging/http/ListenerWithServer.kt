package net.corda.p2p.gateway.messaging.http

internal abstract class ListenerWithServer : HttpServerListener {
    var server: HttpServer? = null
}
