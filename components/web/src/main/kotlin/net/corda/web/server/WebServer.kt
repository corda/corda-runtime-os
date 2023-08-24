package net.corda.web.server

import net.corda.messaging.api.WebContext

interface WebServer{

    val port: Int?

    fun start(port: Int)

    fun stop()

    fun registerHandler(methodType: HTTPMethod, endpoint: String, handle: (WebContext) -> WebContext)

}