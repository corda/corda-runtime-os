package net.corda.httprpc.server

interface HttpRpcServer {

    fun start()
    fun stop()
    fun close()
}