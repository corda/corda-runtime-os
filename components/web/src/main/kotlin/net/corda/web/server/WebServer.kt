package net.corda.web.server

interface WebServer{

    val port: Int?

    fun start(port: Int)

    fun stop()

    fun registerHandler(methodType: HTTPMethod, endpoint: String, handle: (WebContext) -> WebContext)

}