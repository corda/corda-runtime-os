package net.corda.p2p.gateway.messaging.http

interface HttpServerListener: HttpConnectionListener {
    fun onRequest(request: HttpRequest)
}