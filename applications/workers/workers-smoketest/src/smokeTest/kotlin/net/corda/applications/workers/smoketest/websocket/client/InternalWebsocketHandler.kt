package net.corda.applications.workers.smoketest.websocket.client

interface InternalWebsocketHandler {
    val messageQueue: MutableList<String>
    fun isConnected(): Boolean
    fun send(message: String)
}