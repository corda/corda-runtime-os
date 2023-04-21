package net.corda.p2p.gateway.messaging.internal

import net.corda.p2p.gateway.messaging.http.HttpRequest
import net.corda.p2p.gateway.messaging.http.HttpWriter

internal interface RequestListener {
    fun onRequest(httpWriter: HttpWriter, request: HttpRequest)
}
