package net.corda.applications.workers.smoketest.websocket.client

interface InternalWebsocketHandler {
    val messageQueue: List<String>
    fun isConnected(): Boolean
    fun send(message: String)
}