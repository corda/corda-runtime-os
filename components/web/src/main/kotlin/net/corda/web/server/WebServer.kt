package net.corda.web.server

interface WebServer{

    val port: Int?

    fun start(port: Int)

    fun stop()

    fun registerEndpoint(endpoint: Endpoint)

    fun removeEndpoint(endpoint: Endpoint)

}