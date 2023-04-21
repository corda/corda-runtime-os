package net.corda.p2p.gateway.messaging.http

interface HttpClientListener: HttpConnectionListener {
    fun onResponse(httpResponse: HttpResponse)
}