package net.corda.web.api

interface WebServer{

    val port: Int?

    fun start(port: Int)

    fun stop()

    fun registerEndpoint(endpoint: Endpoint)

    fun removeEndpoint(endpoint: Endpoint)

}